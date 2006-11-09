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



import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the server schema, including matching rules,
 * syntaxes, attribute types, and objectclasses.
 */
public class SchemaMessages
{
  /**
   * The message ID for the message that will be used if an attribute syntax
   * cannot retrieve the appropriate approximate matching rule.  This takes two
   * arguments, which are the name of the syntax and the OID for the matching
   * rule.
   */
  public static final int MSGID_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * cannot retrieve the appropriate equality matching rule.  This takes two
   * arguments, which are the name of the syntax and the OID for the matching
   * rule.
   */
  public static final int MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 2;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * cannot retrieve the appropriate ordering matching rule.  This takes two
   * arguments, which are the name of the syntax and the OID for the matching
   * rule.
   */
  public static final int MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 3;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * cannot retrieve the appropriate substring matching rule.  This takes two
   * arguments, which are the name of the syntax and the OID for the matching
   * rule.
   */
  public static final int MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 4;



  /**
   * The message ID for the message that will be used if an illegal value is
   * provided for an attribute with a Boolean syntax.  This takes a single
   * string argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ILLEGAL_BOOLEAN =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 5;



  /**
   * The message ID for the message that will be used if a bit string value is
   * too short to be valid.  This takes a single string argument, which is the
   * provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_BIT_STRING_TOO_SHORT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 6;



  /**
   * The message ID for the message that will be used if a bit string value is
   * not surrounded by single quotes and followed by a capital B.  This takes a
   * single string argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 7;



  /**
   * The message ID for the message that will be used if a bit string value
   * contains a bit element that is not a zero or a one.  This takes two
   * arguments, which are the provided value and the invalid binary digit.
   */
  public static final int MSGID_ATTR_SYNTAX_BIT_STRING_INVALID_BIT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 8;



  /**
   * The message ID for the message that will be used if a country string value
   * has a length that is not exactly two characters.  This takes a single
   * argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_COUNTRY_STRING_INVALID_LENGTH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 9;



  /**
   * The message ID for the message that will be used if a country string value
   * contains non-printable characters.  This takes a single argument, which is
   * the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_COUNTRY_STRING_NOT_PRINTABLE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 10;



  /**
   * The message ID for the message that will be used if a delivery method value
   * does not contain any elements.  This takes a single string argument, which
   * is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_DELIVERY_METHOD_NO_ELEMENTS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 11;



  /**
   * The message ID for the message that will be used if a delivery method value
   * contains an invalid element.  This takes two arguments, which are the
   * provided value and the invalid element.
   */
  public static final int MSGID_ATTR_SYNTAX_DELIVERY_METHOD_INVALID_ELEMENT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 12;



  /**
   * The message ID for the message that will be used if a value is too short
   * to be a valid generalized time value.  This takes a single argument, which
   * is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 13;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a century or year that is not valid.  This takes two
   * arguments, which are the provided value and illegal century or year
   * character.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_YEAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 14;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a month that is not valid.  This takes two arguments, which
   * are the provided value and illegal month substring.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 15;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a day that is not valid.  This takes two arguments, which
   * are the provided value and illegal day substring.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 16;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains an hour that is not valid.  This takes two arguments, which
   * are the provided value and illegal hour substring.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 17;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a minute that is not valid.  This takes two arguments, which
   * are the provided value and illegal minute substring.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MINUTE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 18;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a second that is not valid.  This takes two arguments, which
   * are the provided value and illegal second substring.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SECOND =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 19;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a subsecond that is not valid.  This takes one argument,
   * which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SUBSECOND =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 20;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains a subsecond that is too long.  This takes one argument,
   * which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_LONG_SUBSECOND =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 21;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains an invalid GMT offset.  This takes two arguments, which
   * are the provided value and illegal offset substring.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 22;



  /**
   * The message ID for the message that will be used if a generalized time
   * value contains an illegal character.  This takes three arguments, which
   * are the provided value, the illegal character, and the position of the
   * illegal character.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 23;



  /**
   * The message ID for the message that will be used if a generalized time
   * value cannot be parsed to a Java <CODE>Date</CODE>.  This takes two
   * arguments, which are the provided value and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_CANNOT_PARSE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 24;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly.  This takes two arguments, which are the
   * provided value and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_INVALID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 25;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it ends with a comma.  This takes
   * a single argument, which is the provided DN string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_END_WITH_COMMA =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 26;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * starts with a digit.  This takes two arguments, which are the provided DN
   * string and the illegal digit character.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_START_WITH_DIGIT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 27;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * contains an illegal character.  This takes three arguments, which are the
   * provided DN string, the illegal character, and the position of that
   * character in the string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 28;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * contains an underscore character but attribute name exceptions are not
   * allowed.  This takes two arguments, which are the provided DN string and
   * the name of the configuration option that would allow underscore
   * characters.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_UNDERSCORE_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 29;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * starts with a dash.  This takes a single argument, which is the provided DN
   * string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DASH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 30;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * starts with an underscore.  This takes two arguments, which are the
   * provided DN string and the name of the configuration option that would
   * allow underscore characters as long as they aren't in the first position.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_UNDERSCORE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 31;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * starts with a numeric digit in a way in which it is not allowed.  This
   * takes three arguments, which are the provided DN string, the illegal digit,
   * and the name of the configuration attribute that would allow digits to be
   * used as the first character of an attribute name.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DIGIT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 32;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains a zero-length attribute
   * name.  This takes a single argument, which is the provided DN string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_NO_NAME =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 33;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an attribute name that
   * contains a period but is not an OID.  This takes two arguments, which are
   * the provided DN string and the attribute name..
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_PERIOD =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 34;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it ends with the attribute name.
   * This takes two arguments, which are the provided DN string and the
   * attribute name.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 35;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because the next non-space character after
   * an attribute name is not an equal sign.  This takes three arguments, which
   * are the provided DN string, the attribute name, and the illegal character.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_NO_EQUAL =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 36;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains an invalid character.
   * This takes three arguments, which are the provided DN string, the invalid
   * character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_INVALID_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 37;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains a value that appears to
   * be hex-encoded but without a positive multiple of two hex characters.  This
   * takes a single argument, which is the provided DN string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 38;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it contains a value that appears to
   * be hex-encoded but included an invalid hexadecimal digit.  This takes two
   * arguments, which are the provided DN string and the invalid character.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 39;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because an unexpected failure occurred
   * while trying to parse an RDN component value.  This takes two arguments,
   * which are the provided DN string and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 40;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it has a quoted value that is not
   * properly terminated with another quote.  This takes a single argument,
   * which is the provided DN string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_UNMATCHED_QUOTE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 41;



  /**
   * The message ID for the message that will be used if a distinguished name
   * value cannot be parsed properly because it has a value with an escaped
   * hex digit that is not followed by a second hex digit.  This takes a single
   * argument, which is the provided DN string.
   */
  public static final int MSGID_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 42;



  /**
   * The message ID for the message that will be used if an integer value cannot
   * be parsed because the first digit is zero but the value is not zero.  This
   * takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_INTEGER_INITIAL_ZERO =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 43;



  /**
   * The message ID for the message that will be used if an integer value cannot
   * be parsed because the it contains a dash that is not in the first position.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_INTEGER_MISPLACED_DASH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 44;



  /**
   * The message ID for the message that will be used if an integer value cannot
   * be parsed because it contains an illegal character.  This takes three
   * arguments, which are the provided value, the illegal character, and the
   * position of the illegal character.
   */
  public static final int MSGID_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 45;



  /**
   * The message ID for the message that will be used if an integer value cannot
   * be parsed because it is empty.  This takes a single argument, which is the
   * provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_INTEGER_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 46;



  /**
   * The message ID for the message that will be used if an integer value cannot
   * be parsed because it starts with a dash but doesn't contain a value.  This
   * takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 47;



  /**
   * The message ID for the message that will be used if an OID value cannot
   * be parsed because it is blank.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_OID_NO_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 48;



  /**
   * The message ID for the message that will be used if an OID value cannot
   * be parsed because it contains an illegal character.  This takes two
   * arguments, which are the provided value and the position of the illegal
   * character.
   */
  public static final int MSGID_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 49;



  /**
   * The message ID for the message that will be used if an OID value cannot
   * be parsed because it contains consecutive periods.  This takes two
   * arguments, which are the provided value and the position of the illegal
   * character.
   */
  public static final int MSGID_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 50;



  /**
   * The message ID for the message that will be used if an OID value cannot
   * be parsed because it ends with a period.  This takes a single argument,
   * which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_OID_ENDS_WITH_PERIOD =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 51;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 52;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 53;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 54;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 55;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 56;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_STRING_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 57;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because an illegal character was
   * encountered.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 58;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRTYPE_UNEXPECTED_CLOSE_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 59;



  /**
   * The message ID for the message that will be used if an attribute type
   * description value cannot be parsed because a quotation mark was expected
   * but something else was found.  This takes three arguments, which are the
   * provided value, the name of the token after which the quote was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 60;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references a superior attribute type that is not defined in the
   * server schema.  This takes two arguments, which are the OID of the
   * attribute type description being defined and the name or OID of the
   * superior type that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUPERIOR_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 61;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references an approximate matching rule that is not defined in
   * the server schema.  This takes two arguments, which are the OID of the
   * attribute type description being defined and the OID of the approximate
   * matching rule that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_APPROXIMATE_MR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 62;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references an equality matching rule that is not defined in the
   * server schema.  This takes two arguments, which are the OID of the
   * attribute type description being defined and the OID of the equality
   * matching rule that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_EQUALITY_MR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 63;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references an ordering matching rule that is not defined in the
   * server schema.  This takes two arguments, which are the OID of the
   * attribute type description being defined and the OID of the ordering
   * matching rule that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_ORDERING_MR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 64;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references a substring matching rule that is not defined in the
   * server schema.  This takes two arguments, which are the OID of the
   * attribute type description being defined and the OID of the substring
   * matching rule that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUBSTRING_MR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 65;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references a syntax that is not defined in the server schema.
   * This takes two arguments, which are the OID of the attribute type
   * description being defined and the OID of the syntax that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SYNTAX =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 66;



  /**
   * The message ID for the message that will be used if an attribute type
   * description references an illegal attribute usage.  This takes three
   * arguments, which are the OID of the attribute type description being
   * defined, the specified usage string, and the default usage string that will
   * be used instead.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 67;



  /**
   * The message ID for the message that will be used if an attribute type
   * description cannot be parsed because it contains an unexpected character
   * where a single quote should be.  This takes three arguments, which are the
   * provided attribute type description, the position in which the quote was
   * expected, and the character that was found there instead.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 68;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 69;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int
       MSGID_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_OPEN_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 70;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 71;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int
       MSGID_ATTR_SYNTAX_OBJECTCLASS_DOUBLE_PERIOD_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 72;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 73;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_STRING_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 74;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because an illegal character was
   * encountered.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 75;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int
       MSGID_ATTR_SYNTAX_OBJECTCLASS_UNEXPECTED_CLOSE_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 76;



  /**
   * The message ID for the message that will be used if an objectclass
   * description value cannot be parsed because a quotation mark was expected
   * but something else was found.  This takes three arguments, which are the
   * provided value, the name of the token after which the quote was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_QUOTE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 77;



  /**
   * The message ID for the message that will be used if an objectclass
   * description references a superior objectclass that is not defined in the
   * server schema.  This takes two arguments, which are the OID of the
   * objectclass description being defined and the name or OID of the superior
   * class that should be used.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_SUPERIOR_CLASS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 78;



  /**
   * The message ID for the message that will be used if an objectclass
   * description cannot be parsed because it contains an unexpected character
   * where a single quote should be.  This takes three arguments, which are the
   * provided objectclass description, the position in which the quote was
   * expected, and the character that was found there instead.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 79;



  /**
   * The message ID for the message that will be used if an objectclass
   * description includes a required attribute that is not defined in the server
   * schema.  This takes two arguments, which are the OID of the objectclass
   * being defined, and the name or OID of the requested attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_REQUIRED_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 80;



  /**
   * The message ID for the message that will be used if an objectclass
   * description includes an optional attribute that is not defined in the
   * server schema.  This takes two arguments, which are the OID of the
   * objectclass being defined, and the name or OID of the requested attribute
   * type.
   */
  public static final int MSGID_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_OPTIONAL_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 81;



  /**
   * The message ID for the message that will be used if an attribute is defined
   * with the IA5 syntax but has an illegal character that is not allowed by
   * that syntax.  This takes two arguments, which are the provided value and
   * the illegal character.
   */
  public static final int MSGID_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 82;



  /**
   * The message ID for the message that will be used as the description for the
   * configuration attribute indicating whether a strict telephone number format
   * should be enforced.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEPHONE_DESCRIPTION_STRICT_MODE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_INFORMATIONAL | 83;



  /**
   * The message ID for the message that will be used if a problem occurs when
   * trying to determine whether the telephone number syntax should use strict
   * mode.  This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_ATTR_SYNTAX_TELEPHONE_CANNOT_DETERMINE_STRICT_MODE =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 84;



  /**
   * The message ID for the message that will be used if a proposed telephone
   * number value is empty or null.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEPHONE_EMPTY =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 85;



  /**
   * The message ID for the message that will be used if strict telephone syntax
   * checking is enabled but the provided value does not start with a plus sign.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEPHONE_NO_PLUS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 86;



  /**
   * The message ID for the message that will be used if strict telephone syntax
   * checking is enabled but the provided value contains an illegal character
   * (i.e., not a digit and not a valid separator).  This takes three arguments,
   * which are the provided value, the illegal character and the position of
   * that character.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEPHONE_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 87;



  /**
   * The message ID for the message that will be used if a proposed telephone
   * number value does not contain any numeric digits.  This takes a single
   * argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEPHONE_NO_DIGITS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 88;



  /**
   * The message ID for the message that will be used to indicate that the
   * configuration for the strict telephone number syntax checking has been
   * changed.  This takes two arguments, which are a string representation of
   * the new value, and the DN of the configuration entry.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEPHONE_UPDATED_STRICT_MODE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_INFORMATIONAL | 89;



  /**
   * The message ID for the message that will be used if a numeric string value
   * contains a character that is not a digit or a space.  This takes three
   * arguments, which are the provided value, the illegal character, and the
   * position of that illegal character.
   */
  public static final int MSGID_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 90;



  /**
   * The message ID for the message that will be used if a numeric string value
   * does not contain any characters.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_NUMERIC_STRING_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 91;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRSYNTAX_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 92;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_OPEN_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 93;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 94;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_DOUBLE_PERIOD_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 95;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 96;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_IN_STRING_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 97;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_UNEXPECTED_CLOSE_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 98;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because an error occurred while trying
   * to read the "DESC" token.  This takes three arguments, which are the
   * provided value, the position at which the DESC token was expected, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRSYNTAX_CANNOT_READ_DESC_TOKEN =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 99;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because the "DESC" token was expected
   * but something else was found instead.  This takes two arguments, which are
   * the provided value, and the token that was read instead of "DESC".
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRSYNTAX_TOKEN_NOT_DESC =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 100;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because an error occurred while trying
   * to read the value after the "DESC" token.  This takes three arguments,
   * which are the provided value, the position at which the value was expected,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRSYNTAX_CANNOT_READ_DESC_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 101;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because a closing parenthesis was
   * expected but a different character was found.  This takes three arguments,
   * which are the provided value, the position at which the parenthesis was
   * expected, and the character that was found instead.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_CLOSE_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 102;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description value cannot be parsed because a non-space character was found
   * after the closing parenthesis.  This takes three arguments, which are the
   * provided value, the character that was read, and the position of that
   * character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_AFTER_CLOSE =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 103;



  /**
   * The message ID for the message that will be used if an attribute syntax
   * description cannot be parsed because it contains an unexpected character
   * where a single quote should be.  This takes three arguments, which are the
   * provided attribute type description, the position in which the quote was
   * expected, and the character that was found there instead.
   */
  public static final int MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 104;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a printable string because it is empty or null.  This does not
   * take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_PRINTABLE_STRING_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 105;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a printable string because it contains an illegal character.
   * This takes three arguments, which are the provided value, the illegal
   * character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_PRINTABLE_STRING_ILLEGAL_CHARACTER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 106;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a substring assertion because it consists only of a wildcard.
   * This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_SUBSTRING_ONLY_WILDCARD =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 107;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a substring assertion because it contains consecutive wildcard
   * characters.  This takes two arguments, which are the provided value and the
   * position of the consecutive wildcards.
   */
  public static final int MSGID_ATTR_SYNTAX_SUBSTRING_CONSECUTIVE_WILDCARDS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 108;



  /**
   * The message ID for the message that will be used if a value is too short
   * to be a valid UTC time value.  This takes a single argument, which is the
   * provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_TOO_SHORT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 109;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains a century or year that is not valid.  This takes two arguments,
   * which are the provided value and illegal century or year character.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_YEAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 110;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains a month that is not valid.  This takes two arguments, which are
   * the provided value and illegal month substring.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 111;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains a day that is not valid.  This takes two arguments, which are the
   * provided value and illegal day substring.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_DAY =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 112;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains an hour that is not valid.  This takes two arguments, which are
   * the provided value and illegal hour substring.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 113;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains a minute that is not valid.  This takes two arguments, which are
   * the provided value and illegal minute substring.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_MINUTE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 114;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains an illegal character.  This takes three arguments, which are the
   * provided value, the illegal character, and the position of the illegal
   * character.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 115;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains a second that is not valid.  This takes two arguments, which are
   * the provided value and illegal second substring.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_SECOND =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 116;



  /**
   * The message ID for the message that will be used if a UTC time value
   * contains an invalid GMT offset.  This takes two arguments, which are the
   * provided value and illegal offset substring.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 117;



  /**
   * The message ID for the message that will be used if a UTC time value cannot
   * be parsed to a Java <CODE>Date</CODE>.  This takes two arguments, which are
   * the provided value and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_ATTR_SYNTAX_UTC_TIME_CANNOT_PARSE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 118;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 119;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_EXPECTED_OPEN_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 120;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 121;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_DOUBLE_PERIOD_IN_NUMERIC_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 122;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_NUMERIC_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 123;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_STRING_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 124;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_UNEXPECTED_CLOSE_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 125;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because an illegal character was
   * encountered.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 126;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references an unknown
   * structural objectclass.  This takes two arguments, which are the provided
   * value and the OID of the unknown objectclass.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_UNKNOWN_STRUCTURAL_CLASS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 127;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references a structural
   * objectclass that is not defined as structural.  This takes four arguments,
   * which are the provided value, the OID of the specified objectclass, the
   * user-friendly name for the class, and a string representation of the
   * objectclass type for the specified class.
   */
  public static final int
       MSGID_ATTR_SYNTAX_DCR_STRUCTURAL_CLASS_NOT_STRUCTURAL =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 128;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references an unknown
   * auxiliary objectclass.  This takes two arguments, which are the provided
   * value and the name or OID of the unknown auxiliary objectclass.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 129;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references an auxiliary
   * objectclass that is not defined as auxiliary.  This takes four arguments,
   * which are the provided value, the name or OID of the referenced
   * objectclass, and a string representation of the objectclass type for the
   * specified class
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 130;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references an unknown
   * required attribute type.  This takes two arguments, which are the provided
   * value and the name or OID of the unknown attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 131;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references an unknown
   * optional attribute type.  This takes two arguments, which are the provided
   * value and the name or OID of the unknown attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 132;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because it references an unknown
   * prohibited attribute type.  This takes two arguments, which are the
   * provided value and the name or OID of the unknown attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 133;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * description value cannot be parsed because an invalid character was found
   * where a single quote was expected.  This takes three arguments, which are
   * the provided value, the position at which the quote was expected, and the
   * character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_DCR_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 134;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it is empty.  This does not take any
   * arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 135;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it contains some other character where an
   * open parenthesis was expected.  This takes three arguments, which are the
   * provided value, the position at which the parenthesis was expected, and the
   * character that was found instead.
   */
  public static final int
       MSGID_ATTR_SYNTAX_NAME_FORM_EXPECTED_OPEN_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 136;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because the end of the value was reached while the
   * server still expected to read more information.  This takes a single
   * argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 137;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because the OID contained two consecutive periods.
   * This takes two arguments, which are the provided value and the position of
   * the second period.
   */
  public static final int
       MSGID_ATTR_SYNTAX_NAME_FORM_DOUBLE_PERIOD_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 138;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because the numeric OID contained an illegal
   * character.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_NAME_FORM_ILLEGAL_CHAR_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 139;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because the non-numeric OID contained an illegal
   * character.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_NAME_FORM_ILLEGAL_CHAR_IN_STRING_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 140;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because a close parenthesis was found in an
   * unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int
       MSGID_ATTR_SYNTAX_NAME_FORM_UNEXPECTED_CLOSE_PARENTHESIS =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 141;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because an illegal character was encountered.  This
   * takes three arguments, which are the provided value, the illegal character,
   * and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 142;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it references an unknown structural
   * objectclass.  This takes two arguments, which are the provided value and
   * the OID of the unknown objectclass.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 143;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it references a structural objectclass that
   * is not defined as structural.  This takes four arguments, which are the
   * provided value, the OID of the specified objectclass, the user-friendly
   * name for the class, and a string representation of the objectclass type for
   * the specified class.
   */
  public static final int
       MSGID_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 144;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it references an unknown required attribute
   * type.  This takes two arguments, which are the provided value and the name
   * or OID of the unknown attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 145;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it references an unknown optional attribute
   * type.  This takes two arguments, which are the provided value and the name
   * or OID of the unknown attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 146;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because it does not specify a structural
   * objectclass.  This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_NO_STRUCTURAL_CLASS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 147;



  /**
   * The message ID for the message that will be used if a name form description
   * value cannot be parsed because an invalid character was found where a
   * single quote was expected.  This takes three arguments, which are the
   * provided value, the position at which the quote was expected, and the
   * character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_NAME_FORM_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 148;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 149;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_EXPECTED_OPEN_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 150;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 151;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_DOUBLE_PERIOD_IN_NUMERIC_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 152;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_ILLEGAL_CHAR_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 153;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_ILLEGAL_CHAR_IN_STRING_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 154;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_UNEXPECTED_CLOSE_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 155;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because an illegal character was
   * encountered.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 156;



  /**
   * The message ID for the message that will be used if a matching rule
   * description refers to an attribute a syntax that is not defined in the
   * server schema.  This takes two arguments, which are the provided value and
   * the OID of the unknown attribute syntax.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_UNKNOWN_SYNTAX =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 157;



  /**
   * The message ID for the message that will be used if a matching rule
   * description does not specify the OID of the associated syntax.  This takes
   * a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_NO_SYNTAX =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 158;



  /**
   * The message ID for the message that will be used if a matching rule
   * description value cannot be parsed because an invalid character was found
   * where a single quote was expected.  This takes three arguments, which are
   * the provided value, the position at which the quote was expected, and the
   * character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_MR_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 159;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 160;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_EXPECTED_OPEN_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 161;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 162;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_DOUBLE_PERIOD_IN_NUMERIC_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 163;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_ILLEGAL_CHAR_IN_NUMERIC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 164;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_ILLEGAL_CHAR_IN_STRING_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 165;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because the it is associated with an
   * unknown matching rule.  This takes two arguments, which are the provided
   * value, and the unrecognized OID.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_UNKNOWN_MATCHING_RULE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 166;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_UNEXPECTED_CLOSE_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 167;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because an illegal character was
   * encountered.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 168;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description refers to an attribute type that is not defined in the
   * server schema.  This takes two arguments, which are the provided value and
   * the name or OID of the unknown attribute type.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_UNKNOWN_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 169;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description does not specify any associated attribute types.  This takes
   * a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_NO_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 170;



  /**
   * The message ID for the message that will be used if a matching rule use
   * description value cannot be parsed because an invalid character was found
   * where a single quote was expected.  This takes three arguments, which are
   * the provided value, the position at which the quote was expected, and the
   * character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_MRUSE_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 171;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because it is empty.  This does not take
   * any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 172;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because it contains some other character
   * where an open parenthesis was expected.  This takes three arguments, which
   * are the provided value, the position at which the parenthesis was expected,
   * and the character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_EXPECTED_OPEN_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 173;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because the end of the value was reached
   * while the server still expected to read more information.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_TRUNCATED_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 174;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because the rule ID contained an illegal
   * character.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR_IN_RULE_ID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 175;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because a close parenthesis was found in
   * an unexpected location.  This takes two arguments, which are the provided
   * value and the position of the close parenthesis.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_UNEXPECTED_CLOSE_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 176;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because an illegal character was
   * encountered.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 177;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because it references an unknown name
   * form.  This takes two arguments, which are the provided value and the name
   * or OID of the specified name form.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_UNKNOWN_NAME_FORM =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 178;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because it references an unknown
   * superior rule ID.  This takes two arguments, which are the provided value
   * and the rule ID of the superior DIT structure rule.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 179;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because it does not specify a name form.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_NO_NAME_FORM =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 180;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because an invalid character was found
   * where a single quote was expected.  This takes three arguments, which are
   * the provided value, the position at which the quote was expected, and the
   * character that was found instead.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_EXPECTED_QUOTE_AT_POS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 181;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because the OID contained two
   * consecutive periods.  This takes two arguments, which are the provided
   * value and the position of the second period.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_DOUBLE_PERIOD_IN_NUMERIC_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 182;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because the numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR_IN_NUMERIC_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 183;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * description value cannot be parsed because the non-numeric OID contained an
   * illegal character.  This takes three arguments, which are the provided
   * value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR_IN_STRING_OID =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 184;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a telex number because it is too short to hold a valid number.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEX_TOO_SHORT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 185;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a telex number because it contains an illegal character where a
   * printable string character should be.  This takes three arguments, which
   * are the provided value, the illegal character, and the position of that
   * character.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEX_NOT_PRINTABLE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 186;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a telex number because it contains an illegal character where a
   * printable string character or dollar sign should be.  This takes three
   * arguments, which are the provided value, the illegal character, and the
   * position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 187;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a telex number because the end of the value was found before the
   * complete number could be read.  This takes a single argument, which is the
   * provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_TELEX_TRUNCATED =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 188;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a facsimile telephone number because the value was empty.  This
   * does not take any arguments
   */
  public static final int MSGID_ATTR_SYNTAX_FAXNUMBER_EMPTY =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 189;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a facsimile telephone number because the telephone number portion
   * contains an invalid character that is not from the printable string
   * character set.  This takes three arguments, which are the provided value,
   * the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_FAXNUMBER_NOT_PRINTABLE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 190;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a facsimile telephone number because it ends with a dollar sign.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_FAXNUMBER_END_WITH_DOLLAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 191;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a facsimile telephone number because it contains an illegal fax
   * parameter.  This takes four arguments, which are the provided value, the
   * illegal fax parameter, the start position, and the end position for that
   * parameter.
   */
  public static final int MSGID_ATTR_SYNTAX_FAXNUMBER_ILLEGAL_PARAMETER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 192;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a name and optional UID because an error occurred while trying to
   * parse and normalize the DN portion.  This takes two arguments, which are
   * the provided value and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_ATTR_SYNTAX_NAMEANDUID_INVALID_DN =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 193;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a name and optional UID because it contained an illegal binary
   * digit in the uid portion.  This takes three arguments, which are the
   * provided value, the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_NAMEANDUID_ILLEGAL_BINARY_DIGIT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 194;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a teletex terminal identifier because the value was empty.  This
   * does not take any arguments
   */
  public static final int MSGID_ATTR_SYNTAX_TELETEXID_EMPTY =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 195;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a teletex terminal identifier because the terminal identifier
   * portion contains an invalid character that is not from the printable string
   * character set.  This takes three arguments, which are the provided value,
   * the illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_TELETEXID_NOT_PRINTABLE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 196;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a teletex terminal identifier because it ends with a dollar sign.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_TELETEXID_END_WITH_DOLLAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 197;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a teletex terminal identifier because a TTX parameter does not
   * contain a colon to separate the name from the value.  This takes two
   * arguments, which are the provided value and the invalid parameter string.
   */
  public static final int MSGID_ATTR_SYNTAX_TELETEXID_PARAM_NO_COLON =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 198;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a teletex terminal identifier because it contains an illegal TTX
   * parameter.  This takes two arguments, which are the provided value and the
   * invalid parameter name.
   */
  public static final int MSGID_ATTR_SYNTAX_TELETEXID_ILLEGAL_PARAMETER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 199;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an other mailbox because the provided value was empty.  This does
   * not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_OTHER_MAILBOX_EMPTY_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 200;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an other mailbox because the it did not specify the mailbox type.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_OTHER_MAILBOX_NO_MBTYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 201;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an other mailbox because the mailbox type contained an illegal
   * character.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MBTYPE_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 202;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an other mailbox because the it did not specify the mailbox.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_OTHER_MAILBOX_NO_MAILBOX =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 203;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an other mailbox because the mailbox contained an illegal
   * character.  This takes three arguments, which are the provided value, the
   * illegal character, and the position of that character.
   */
  public static final int MSGID_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MB_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 204;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it did not specify an objectclass.  This takes a
   * single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_NO_OC =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 205;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it contains an illegal character in the criteria.
   * This takes four arguments, which are the provided value, the criteria
   * portion, the illegal character, and the position of the illegal character
   * thin the criteria portion.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_ILLEGAL_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 206;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it starts with an open parenthesis but does not
   * contain a matching close parenthesis.  This takes two arguments, which are
   * the provided value and the criteria portion.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_MISSING_CLOSE_PAREN =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 207;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it contains a criteria portion that starts with a
   * question mark but is not "?true" or "?false".  This takes two arguments,
   * which are the provided value and the criteria portion.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_INVALID_QUESTION_MARK =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 208;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it has a criteria with no dollar sign.  This
   * takes two arguments, which are the provided value and the criteria portion.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_NO_DOLLAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 209;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it has a criteria with no attribute type.  This
   * takes two arguments, which are the provided value and the criteria portion.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_NO_ATTR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 210;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it has a criteria with no match type.  This takes
   * two arguments, which are the provided value and the criteria portion.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_NO_MATCH_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 211;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a guide because it has a criteria with an invalid match type.
   * This takes three arguments, which are the provided value, the criteria
   * portion, and the position of the invalid match type.
   */
  public static final int MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 212;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because it did not contain an octothorpe to
   * separate the objectclass from the criteria.  This takes a single argument,
   * which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SHARP =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 213;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because it did not specify an objectclass.
   * This takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_OC =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 214;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because the objectclass OID had two consecutive
   * periods.  This takes three arguments, which are the provided value, the
   * objectclass OID, and the position of the double period.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_DOUBLE_PERIOD_IN_OC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 215;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because the objectclass OID had an illegal
   * character.  This takes four arguments, which are the provided value, the
   * objectclass OID, the illegal character, and the position of that character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_ILLEGAL_CHAR_IN_OC_OID =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 216;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because the objectclass name had an illegal
   * character.  This takes four arguments, which are the provided value, the
   * objectclass name, the illegal character, and the position of that
   * character.
   */
  public static final int
       MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_ILLEGAL_CHAR_IN_OC_NAME =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 217;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because it did not contain an octothorpe to
   * separate the criteria from the scope.  This takes a single argument, which
   * is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_FINAL_SHARP =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 218;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because it did not contain a scope after the
   * final octothorpe.  This takes a single argument, which is the provided
   * value.
   */
  public static final int MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SCOPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 219;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because it contained an invalid scope.  This
   * takes two arguments, which are the provided value and the invalid scope.
   */
  public static final int MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_INVALID_SCOPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 220;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an enhanced guide because it did not contain any criteria.  This
   * takes a single argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_CRITERIA =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 221;



  /**
   * The message ID for the message that will be used if an OID value cannot
   * be parsed because it contains an invalid value.  This takes two arguments,
   * which are the provided value and a message explaining the problem.
   */
  public static final int MSGID_ATTR_SYNTAX_OID_INVALID_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 222;



  /**
   * The message ID for the message that will be used if an unexpected failure
   * occurs while attempting to normalize a generalized time value.  This takes
   * two arguments, which are the provided value and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 223;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two attribute value objects using the case exact
   * ordering matching rule's <CODE>compare</CODE> method and it is not possible
   * to obtain the normalized form of one of the values.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_OMR_CASE_EXACT_COMPARE_CANNOT_NORMALIZE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 224;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two objects using the case exact ordering matching
   * rule's <CODE>compare</CODE> method but the provided objects were of a type
   * that is not supported for comparison.  This takes a single argument, which
   * is the fully-qualified name of the class with which the provided object
   * is associated.
   */
  public static final int MSGID_OMR_CASE_EXACT_COMPARE_INVALID_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 225;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two attribute value objects using the case ignore
   * ordering matching rule's <CODE>compare</CODE> method and it is not possible
   * to obtain the normalized form of one of the values.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_OMR_CASE_IGNORE_COMPARE_CANNOT_NORMALIZE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 226;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two objects using the case ignore ordering matching
   * rule's <CODE>compare</CODE> method but the provided objects were of a type
   * that is not supported for comparison.  This takes a single argument, which
   * is the fully-qualified name of the class with which the provided object
   * is associated.
   */
  public static final int MSGID_OMR_CASE_IGNORE_COMPARE_INVALID_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 227;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two attribute value objects using the generalized
   * time ordering matching rule's <CODE>compare</CODE> method and it is not
   * possible to obtain the normalized form of one of the values.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_OMR_GENERALIZED_TIME_COMPARE_CANNOT_NORMALIZE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 228;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two objects using the generalized time ordering
   * matching rule's <CODE>compare</CODE> method but the provided objects were
   * of a type that is not supported for comparison.  This takes a single
   * argument, which is the fully-qualified name of the class with which the
   * provided object is associated.
   */
  public static final int MSGID_OMR_GENERALIZED_TIME_COMPARE_INVALID_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 229;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two attribute value objects using the integer
   * ordering matching rule's <CODE>compare</CODE> method and it is not possible
   * to obtain the normalized form of one of the values.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_OMR_INTEGER_COMPARE_CANNOT_NORMALIZE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 230;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two objects using the integer ordering matching
   * rule's <CODE>compare</CODE> method but the provided objects were of a type
   * that is not supported for comparison.  This takes a single argument, which
   * is the fully-qualified name of the class with which the provided object
   * is associated.
   */
  public static final int MSGID_OMR_INTEGER_COMPARE_INVALID_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 231;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two attribute value objects using the numeric string
   * ordering matching rule's <CODE>compare</CODE> method and it is not possible
   * to obtain the normalized form of one of the values.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_OMR_NUMERIC_STRING_COMPARE_CANNOT_NORMALIZE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 232;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two objects using the numeric string ordering
   * matching rule's <CODE>compare</CODE> method but the provided objects were
   * of a type that is not supported for comparison.  This takes a single
   * argument, which is the fully-qualified name of the class with which the
   * provided object is associated.
   */
  public static final int MSGID_OMR_NUMERIC_STRING_COMPARE_INVALID_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 233;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two attribute value objects using the octet string
   * ordering matching rule's <CODE>compare</CODE> method and it is not possible
   * to obtain the normalized form of one of the values.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_OMR_OCTET_STRING_COMPARE_CANNOT_NORMALIZE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 234;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to compare two objects using the octet string ordering matching
   * rule's <CODE>compare</CODE> method but the provided objects were of a type
   * that is not supported for comparison.  This takes a single argument, which
   * is the fully-qualified name of the class with which the provided object is
   * is associated.
   */
  public static final int MSGID_OMR_OCTET_STRING_COMPARE_INVALID_TYPE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 235;



  /**
   * The message ID for the message that will be used if a UUID value has an
   * invalid length.  This takes two arguments, which are the provided value and
   * the actual length of that value.
   */
  public static final int MSGID_ATTR_SYNTAX_UUID_INVALID_LENGTH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 236;



  /**
   * The message ID for the message that will be used if a UUID value has
   * a non-dash character at a position that should contain a dash.  This takes
   * three arguments, which are the provided value, the position of the illegal
   * character, and the character that was found in that position.
   */
  public static final int MSGID_ATTR_SYNTAX_UUID_EXPECTED_DASH =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 237;



  /**
   * The message ID for the message that will be used if a UUID value has
   * a non-dash character at a position that should contain a hexadecimal digit.
   * This takes three arguments, which are the provided value, the position of
   * the illegal character, and the character that was found in that position.
   */
  public static final int MSGID_ATTR_SYNTAX_UUID_EXPECTED_HEX =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 238;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that controls whether directory string attributes
   * will be allowed to have zero-length values.  It does not take any
   * arguments.
   */
  public static final int
       MSGID_ATTR_SYNTAX_DIRECTORYSTRING_DESCRIPTION_ALLOW_ZEROLENGTH =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_INFORMATIONAL | 239;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to allow zero-length values for directory
   * string attributes.  This takes two arguments, which are the name of the
   * configuration attribute that controls this behavior and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_ATTR_SYNTAX_DIRECTORYSTRING_CANNOT_DETERMINE_ZEROLENGTH =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 240;



  /**
   * The message ID for the message that will be used if a directory string
   * attribute is assigned a zero-length value.  This does not take any
   * arguments.
   */
  public static final int
       MSGID_ATTR_SYNTAX_DIRECTORYSTRING_INVALID_ZEROLENGTH_VALUE =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 241;



  /**
   * The message ID for the message that will be used if the configuration
   * attribute controlling whether to accept zero-length values is updated.
   * This takes three arguments, which are the name of the configuration
   * attribute, the DN of the configuration entry, and a string representation
   * of the new value.
   */
  public static final int
       MSGID_ATTR_SYNTAX_DIRECTORYSTRING_UPDATED_ALLOW_ZEROLENGTH =
            CATEGORY_MASK_SCHEMA | SEVERITY_MASK_INFORMATIONAL | 242;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an invalid character in the scheme name.  This takes a single argument,
   * which is the position of the invalid character.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_INVALID_SCHEME_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 243;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an empty scheme.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_NO_SCHEME =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 244;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an invalid character between the scheme and authInfo elements.  This
   * does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_NO_SCHEME_SEPARATOR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 245;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an invalid character in the authInfo element.  This takes a single
   * argument, which is the position of the invalid character.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_INFO_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 246;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an empty authInfo element.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 247;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an invalid character between the authInfo and authValue elements.  This
   * does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO_SEPARATOR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 248;



  /**
   * The message ID for the message that will be used if a value does not start
   * with an opening parenthesis.  This takes a single argument, which is the
   * provided value string.
   */
  public static final int MSGID_EMR_INTFIRSTCOMP_NO_INITIAL_PARENTHESIS =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 249;



  /**
   * The message ID for the message that will be used if a value does not have
   * any non-space characters after the opening parenthesis.  This takes a
   * single argument, which is the provided value string.
   */
  public static final int MSGID_EMR_INTFIRSTCOMP_NO_NONSPACE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 250;



  /**
   * The message ID for the message that will be used if a value does not have
   * any space characters after the first component.  This takes a single
   * argument, which is the provided value string.
   */
  public static final int MSGID_EMR_INTFIRSTCOMP_NO_SPACE_AFTER_INT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 251;



  /**
   * The message ID for the message that will be used if the first component
   * cannot be decoded as an integer.  This takes a single argument, which is
   * the provided value string.
   */
  public static final int MSGID_EMR_INTFIRSTCOMP_FIRST_COMPONENT_NOT_INT =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 252;



  /**
   * The message ID for the message that will be used if a provided userpassword
   * value is null or empty.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_USERPW_NO_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 253;



  /**
   * The message ID for the message that will be used if a provided userpassword
   * value does not start with an opening curly brace.  This does not take any
   * arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 254;



  /**
   * The message ID for the message that will be used if a provided userpassword
   * value does not contain a closing curly brace.  This does not take any
   * arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 255;



  /**
   * The message ID for the message that will be used if a provided userpassword
   * value does not contain a storage scheme name.  This does not take any
   * arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_USERPW_NO_SCHEME =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 256;



  /**
   * The message ID for the message that will be used if an RFC 3672
   * subtree specification value cannot be parsed properly. This takes a
   * single argument: the provided invalid value.
   */
  public static final int
    MSGID_ATTR_SYNTAX_RFC3672_SUBTREE_SPECIFICATION_INVALID =
        CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 257;



  /**
   * The message ID for the message that will be used if an absolute
   * subtree specification value cannot be parsed properly. This takes a
   * single argument: the provided invalid value.
   */
  public static final int
    MSGID_ATTR_SYNTAX_ABSOLUTE_SUBTREE_SPECIFICATION_INVALID =
        CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 258;



  /**
   * The message ID for the message that will be used if a relative
   * subtree specification value cannot be parsed properly. This takes a
   * single argument: the provided invalid value.
   */
  public static final int
    MSGID_ATTR_SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_INVALID =
        CATEGORY_MASK_SCHEMA | SEVERITY_MASK_MILD_ERROR | 259;



  /**
   * The message ID for the message that will be used if an illegal value is
   * provided for an attribute with an Integer syntax.  This takes a single
   * string argument, which is the provided value.
   */
  public static final int MSGID_ATTR_SYNTAX_ILLEGAL_INTEGER =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_WARNING | 260;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an invalid character in the authValue element.  This takes a single
   * argument, which is the position of the invalid character.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_VALUE_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 261;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an empty authValue element.  This does not take any arguments.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_NO_AUTH_VALUE =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 262;



  /**
   * The message ID for the message that will be used if an authPassword value
   * has an invalid character after the authValue element.  This takes a single
   * argument, which is the position of the invalid character.
   */
  public static final int MSGID_ATTR_SYNTAX_AUTHPW_INVALID_TRAILING_CHAR =
       CATEGORY_MASK_SCHEMA | SEVERITY_MASK_SEVERE_ERROR | 263;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE,
                    "Unable to retrieve approximate matching rule %s used as " +
                    "the default for the %s attribute syntax.  Approximate " +
                    "matching will not be allowed by default for attributes " +
                    "with this syntax.");
    registerMessage(MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
                    "Unable to retrieve equality matching rule %s used as " +
                    "the default for the %s attribute syntax.  Equality " +
                    "matching will not be allowed by default for attributes " +
                    "with this syntax.");
    registerMessage(MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
                    "Unable to retrieve ordering matching rule %s used as " +
                    "the default for the %s attribute syntax.  Ordering " +
                    "matches will not be allowed by default for attributes " +
                    "with this syntax.");
    registerMessage(MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
                    "Unable to retrieve substring matching rule %s used as " +
                    "the default for the %s attribute syntax.  Substring " +
                    "matching will not be allowed by default for attributes " +
                    "with this syntax.");


    registerMessage(MSGID_ATTR_SYNTAX_ILLEGAL_BOOLEAN,
                    "The provided value %s is not allowed for attributes " +
                    "with a Boolean syntax.  The only allowed values are " +
                    "'TRUE' and 'FALSE'.");


    registerMessage(MSGID_ATTR_SYNTAX_BIT_STRING_TOO_SHORT,
                    "The provided value %s is too short to be a valid bit " +
                    "string.  A bit string must be a series of binary digits " +
                    "surrounded by single quotes and followed by a capital " +
                    "letter B.");
    registerMessage(MSGID_ATTR_SYNTAX_BIT_STRING_NOT_QUOTED,
                    "The provided value %s is not a valid bit string because " +
                    "it is not surrounded by single quotes and followed by a " +
                    "capital letter B.");
    registerMessage(MSGID_ATTR_SYNTAX_BIT_STRING_INVALID_BIT,
                    "The provided value %s is not a valid bit string because " +
                    "%s is not a valid binary digit.");


    registerMessage(MSGID_ATTR_SYNTAX_COUNTRY_STRING_INVALID_LENGTH,
                    "The provided value %s is not a valid country string " +
                    "because the length is not exactly two characters.");
    registerMessage(MSGID_ATTR_SYNTAX_COUNTRY_STRING_NOT_PRINTABLE,
                    "The provided value %s is not a valid country string " +
                    "because it contains one or more non-printable " +
                    "characters.");


    registerMessage(MSGID_ATTR_SYNTAX_DELIVERY_METHOD_NO_ELEMENTS,
                    "The provided value %s is not a valid delivery method " +
                    "value because it does not contain any elements.");
    registerMessage(MSGID_ATTR_SYNTAX_DELIVERY_METHOD_INVALID_ELEMENT,
                    "The provided value %s is not a valid delivery method " +
                    "value because %s is not a valid method.");


    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_TOO_SHORT,
                    "The provided value %s is too short to be a valid " +
                    "generalized time value.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_YEAR,
                    "The provided value %s is not a valid generalized time " +
                    "value because the %s character is not allowed in the " +
                    "century or year specification.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MONTH,
                    "The provided value %s is not a valid generalized time " +
                    "value because %s is not a valid month specification.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_DAY,
                    "The provided value %s is not a valid generalized time " +
                    "value because %s is not a valid day specification.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_HOUR,
                    "The provided value %s is not a valid generalized time " +
                    "value because %s is not a valid hour specification.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_MINUTE,
                    "The provided value %s is not a valid generalized time " +
                    "value because %s is not a valid minute specification.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SECOND,
                    "The provided value %s is not a valid generalized time " +
                    "value because %s is not a valid second specification.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_SUBSECOND,
                    "The provided value %s is not a valid generalized time " +
                    "value because the sub-second component is not valid " +
                    "(between 1 and 3 numeric digits).");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_LONG_SUBSECOND,
                    "The provided value %s is not a valid generalized time " +
                    "value because the sub-second value may not contain more " +
                    "than three digits.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_OFFSET,
                    "The provided value %s is not a valid generalized time " +
                    "value because %s is not a valid GMT offset.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_INVALID_CHAR,
                    "The provided value %s is not a valid generalized time " +
                    "value because it contains an invalid character %s at " +
                    "position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_CANNOT_PARSE,
                    "The provided value %s could not be parsed as a valid " +
                    "generalized time:  %s.");
    registerMessage(MSGID_ATTR_SYNTAX_GENERALIZED_TIME_NORMALIZE_FAILURE,
                    "An unexpected error occurred while trying to normalize " +
                    "value %s as a generalized time value:  %s.");


    registerMessage(MSGID_ATTR_SYNTAX_DN_INVALID,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name:  %s.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_END_WITH_COMMA,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the last non-space character " +
                    "was a comma or semicolon.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_START_WITH_DIGIT,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because numeric digit %s is not " +
                    "allowed as the first character in an attribute name");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because character %s at position %d " +
                    "is not allowed in an attribute name.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_UNDERSCORE_CHAR,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the underscore character is " +
                    "not allowed in an attribute name unless the %s " +
                    " configuration option is enabled.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DASH,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the hyphen character is not " +
                    "allowed as the first character of an attribute name.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_UNDERSCORE,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the underscore character is " +
                    "not allowed as the first character of an attribute name " +
                    "even if the %s configuration option is enabled.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DIGIT,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the digit %s is not allowed " +
                    "allowed as the first character of an attribute name " +
                    "unless the name is specified as an OID or the %s " +
                    " configuration option is enabled.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_NO_NAME,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because it contained an RDN " +
                    "containing an empty attribute name.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_PERIOD,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the parsed attribute name %s " +
                    "included a period but that name did not appear to be a " +
                    "valid OID.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the last non-space character " +
                    "was part of the attribute name %s.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_NO_EQUAL,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because the next non-space character " +
                    "after attribute name %s should have been an equal sign " +
                    "but instead was %s.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_INVALID_CHAR,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because character %s at position %d " +
                    "is not valid.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because an attribute value started " +
                    "with an octothorpe (#) but was not followed by a " +
                    "positive multiple of two hexadecimal digits.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because an attribute value started " +
                    "with an octothorpe (#) but contained a character %s " +
                    "that was not a valid hexadecimal digit.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because an unexpected failure " +
                    "occurred while attempting to parse an attribute value " +
                    "from one of the RDN components:  %s.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_UNMATCHED_QUOTE,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because one of the RDN components " +
                    "included a quoted value that did not have a " +
                    "corresponding closing quotation mark.");
    registerMessage(MSGID_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID,
                    "The provided value %s could not be parsed as a valid " +
                    "distinguished name because one of the RDN components " +
                    "included a value with an escaped hexadecimal digit that " +
                    "was not followed by a second hexadecimal digit.");


    registerMessage(MSGID_ATTR_SYNTAX_INTEGER_INITIAL_ZERO,
                    "The provided value %s could not be parsed as a valid " +
                    "integer because the first digit may not be zero unless " +
                    "it is the only digit.");
    registerMessage(MSGID_ATTR_SYNTAX_INTEGER_MISPLACED_DASH,
                    "The provided value %s could not be parsed as a valid " +
                    "integer because the dash may only appear if it is the " +
                    "first character of the value followed by one or more " +
                    "digits.");
    registerMessage(MSGID_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER,
                    "The provided value %s could not be parsed as a valid " +
                    "integer because character %s at position %d is not " +
                    "allowed in an integer value.");
    registerMessage(MSGID_ATTR_SYNTAX_INTEGER_EMPTY_VALUE,
                    "The provided value %s could not be parsed as a valid " +
                    "integer because it did not contain any digits.");
    registerMessage(MSGID_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE,
                    "The provided value %s could not be parsed as a valid " +
                    "integer because it contained only a dash not followed " +
                    "by an integer value.");
    registerMessage(MSGID_ATTR_SYNTAX_ILLEGAL_INTEGER,
                    "The provided value %s is not allowed for attributes " +
                    "with a Integer syntax.");


    registerMessage(MSGID_ATTR_SYNTAX_OID_NO_VALUE,
                    "The provided value could not be parsed as a valid OID " +
                    "because it did not contain any characters.");
    registerMessage(MSGID_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER,
                    "The provided value %s could not be parsed as a valid " +
                    "OID because it had an illegal character at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS,
                    "The provided value %s could not be parsed as a valid " +
                    "OID because it had two consecutive periods at or near " +
                    "position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OID_ENDS_WITH_PERIOD,
                    "The provided value %s could not be parsed as a valid " +
                    "OID because it ends with a period.");
    registerMessage(MSGID_ATTR_SYNTAX_OID_INVALID_VALUE,
                    "The provided value %s could not be parsed as a valid " +
                    "OID:  %s.");


    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid " +
                    "attribute type description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because an open parenthesis " +
                    "was expected at position %d but instead a '%s' " +
                    "character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because the end of the " +
                    "value was encountered while the Directory Server " +
                    "expected more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because the non-numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because it contained an " +
                    "illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because a single quote was " +
                    "expected as the first non-blank character following " +
                    "token %s.  However, the character %s was found instead.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUPERIOR_TYPE,
                    "The definition for the attribute type with OID %s " +
                    "declared a superior type with an OID of %s.  No " +
                    "attribute type with this OID exists in the server " +
                    "schema, so the Directory Server will use a generic " +
                    "attribute type as the superior type for this definition.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_APPROXIMATE_MR,
                    "The definition for the attribute type with OID %s " +
                    "declared that approximate matching should be performed " +
                    "using the matching rule \"%s\".  No such approximate " +
                    "matching rule is configured for use in the Directory " +
                    "Server, so the default approximate matching rule for " +
                    "the attribute's syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_EQUALITY_MR,
                    "The definition for the attribute type with OID %s " +
                    "declared that equality matching should be performed " +
                    "using the matching rule \"%s\".  No such equality " +
                    "matching rule is configured for use in the Directory " +
                    "Server, so the default equality matching rule for the " +
                    "attribute's syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_ORDERING_MR,
                    "The definition for the attribute type with OID %s " +
                    "declared that ordering matching should be performed " +
                    "using the matching rule \"%s\".  No such ordering " +
                    "matching rule is configured for use in the Directory " +
                    "Server, so the default ordering matching rule for the " +
                    "attribute's syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUBSTRING_MR,
                    "The definition for the attribute type with OID %s " +
                    "declared that substring matching should be performed " +
                    "using the matching rule \"%s\".  No such substring " +
                    "matching rule is configured for use in the Directory " +
                    "Server, so the default substring matching rule for the " +
                    "attribute's syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SYNTAX,
                    "The definition for the attribute type with OID %s " +
                    "declared that it should have a syntax with OID %s.  No " +
                    "such syntax is configured for use in the Directory " +
                    "Server, so the default attribute syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE,
                    "The definition for the attribute type with OID %s " +
                    "declared that it should have an attribute usage of " +
                    "%s.  This is an invalid usage, so the default usage of " +
                    "%s will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute type description because a single quote was " +
                    "expected at position %d but the character %s was found " +
                    "instead.");


    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid " +
                    "objectclass description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because an open parenthesis was " +
                    "expected at position %d but instead a '%s' character " +
                    "was found.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because the end of the value " +
                    "was encountered while the Directory Server expected " +
                    "more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because the non-numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because it contained an illegal " +
                    "character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_QUOTE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because a single quote was " +
                    "expected as the first non-blank character following " +
                    "token %s.  However, the character %s was found instead.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_SUPERIOR_CLASS,
                    "The definition for the objectclass with OID %s declared " +
                    "a superior objectclass with an OID of %s.  No " +
                    "objectclass with this OID exists in the server schema, " +
                    "so the Directory Server will use the top objectclass as " +
                    "the superior class for this definition.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "objectclass description because a single quote was " +
                    "expected at position %d but the character %s was found " +
                    "instead.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_REQUIRED_ATTR,
                    "The definition for the objectclass with OID %s declared " +
                    "that it should include required attribute \"%s\".  No " +
                    "attribute type matching this name or OID exists in the " +
                    "server schema, so a default attribute type with the " +
                    "directory string syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_OPTIONAL_ATTR,
                    "The definition for the objectclass with OID %s declared " +
                    "that it should include optional attribute \"%s\".  No " +
                    "attribute type matching this name or OID exists in the " +
                    "server schema, so a default attribute type with the " +
                    "directory string syntax will be used.");


    registerMessage(MSGID_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER,
                    "The provided value \"%s\" cannot be parsed as a valid " +
                    "IA5 string because it contains an illegal character " +
                    "\"%s\" that is not allowed in the IA5 (ASCII) character " +
                    "set.");


    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_DESCRIPTION_STRICT_MODE,
                    "This indicates whether the telephone number attribute " +
                    "syntax should use a strict mode in which it will only " +
                    "accept values in the ITU-T E.123 format.  If this is " +
                    "enabled, then any value not in this format will be " +
                    "rejected.  If this is disabled, then any value will " +
                    "be accepted, but only the digits will be considered " +
                    "when performing matching.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_CANNOT_DETERMINE_STRICT_MODE,
                    "An error occurred while trying to retrieve attribute " +
                    ATTR_TELEPHONE_STRICT_MODE + " from configuration entry " +
                    "%s:  %s.  The Directory Server will not enforce strict " +
                    "compliance to the ITU-T E.123 format for telephone " +
                    "number values.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_EMPTY,
                    "The provided value is not a valid telephone number " +
                    "because it is empty or null.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_NO_PLUS,
                    "The provided value \"%s\" is not a valid telephone " +
                    "number because strict telephone number checking is " +
                    "enabled and the value does not start with a plus sign " +
                    "in compliance with the ITU-T E.123 specification.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_ILLEGAL_CHAR,
                    "The provided value \"%s\" is not a valid telephone " +
                    "number because strict telephone number checking is " +
                    "enabled and the character %s at position %d is not " +
                    "allowed by the ITU-T E.123 specification.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_NO_DIGITS,
                    "The provided value \"%s\" is not a valid telephone " +
                    "number because it does not contain any numeric digits.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEPHONE_UPDATED_STRICT_MODE,
                    "The value of configuration attribute " +
                    ATTR_TELEPHONE_STRICT_MODE + ", which indicates whether " +
                    "to use strict telephone number syntax checking, has " +
                    "been updated to %s in configuration entry %s.");


    registerMessage(MSGID_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR,
                    "The provided value \"%s\" is not a valid numeric string " +
                    "because it contained character %s at position %d that " +
                    "was neither a digit nor a space.");
    registerMessage(MSGID_ATTR_SYNTAX_NUMERIC_STRING_EMPTY_VALUE,
                    "The provided value is not a valid numeric string " +
                    "because it did not contain any characters.  A numeric " +
                    "string value must contain at least one numeric digit " +
                    "or space.");


    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid " +
                    "attribute syntax description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because an open " +
                    "parenthesis was expected at position %d but instead a " +
                    "'%s' character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because the end of the " +
                    "value was encountered while the Directory Server " +
                    "expected more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because the non-numeric " +
                    "OID contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_CANNOT_READ_DESC_TOKEN,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because an unexpected " +
                    "error occurred while trying to read the \"DESC\" token " +
                    "from the string at or near position %d:  %s.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_TOKEN_NOT_DESC,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because the \"DESC\" token " +
                    "was expected but the string \"%s\" was found instead.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_CANNOT_READ_DESC_VALUE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because an unexpected " +
                    "error occurred while trying to read the value of the " +
                    "\"DESC\" token from the string at or near position %d:  " +
                    "%s.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because a close " +
                    "parenthesis was expected at position %d but instead a " +
                    "'%s' character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_AFTER_CLOSE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because an illegal " +
                    "character %s was found at position %d after the close " +
                    "parenthesis.");
    registerMessage(MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as an " +
                    "attribute syntax description because a single quote was " +
                    "expected at position %d but the character %s was found " +
                    "instead.");


    registerMessage(MSGID_ATTR_SYNTAX_PRINTABLE_STRING_EMPTY_VALUE,
                    "The provided value could not be parsed as a printable " +
                    "string because it was null or empty.  A printable " +
                    "string must contain at least one character.");
    registerMessage(MSGID_ATTR_SYNTAX_PRINTABLE_STRING_ILLEGAL_CHARACTER,
                    "The provided value \"%s\" could not be parsed as a " +
                    "printable string because it contained an invalid " +
                    "character %s at position %d.");


    registerMessage(MSGID_ATTR_SYNTAX_SUBSTRING_ONLY_WILDCARD,
                    "The provided value \"*\" could not be parsed as a " +
                    "substring assertion because it consists only of a " +
                    "wildcard character and zero-length substrings are not " +
                    "allowed.");
    registerMessage(MSGID_ATTR_SYNTAX_SUBSTRING_CONSECUTIVE_WILDCARDS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "substring assertion because it contains consecutive " +
                    "wildcard characters at position %d and zero-length " +
                    "substrings are not allowed.");


    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_TOO_SHORT,
                    "The provided value %s is too short to be a valid UTC " +
                    "time value.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_YEAR,
                    "The provided value %s is not a valid UTC time value " +
                    "because the %s character is not allowed in the century " +
                    "or year specification.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_MONTH,
                    "The provided value %s is not a valid UTC time value " +
                    "because %s is not a valid month specification.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_DAY,
                    "The provided value %s is not a valid UTC time value " +
                    "because %s is not a valid day specification.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_HOUR,
                    "The provided value %s is not a valid UTC time value " +
                    "because %s is not a valid hour specification.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_MINUTE,
                    "The provided value %s is not a valid UTC time value " +
                    "because %s is not a valid minute specification.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_CHAR,
                    "The provided value %s is not a valid UTC time value " +
                    "because it contains an invalid character %s at position " +
                    "%d.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_SECOND,
                    "The provided value %s is not a valid UTC time value " +
                    "because %s is not a valid second specification.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_INVALID_OFFSET,
                    "The provided value %s is not a valid UTC time value " +
                    "because %s is not a valid GMT offset.");
    registerMessage(MSGID_ATTR_SYNTAX_UTC_TIME_CANNOT_PARSE,
                    "The provided value %s could not be parsed as a valid " +
                    "UTC time:  %s.");


    registerMessage(MSGID_ATTR_SYNTAX_DCR_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid DIT " +
                    "content rule description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because an open parenthesis " +
                    "was expected at position %d but instead a '%s' " +
                    "character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because the end of the value " +
                    "was encountered while the Directory Server expected " +
                    "more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because the non-numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_UNKNOWN_STRUCTURAL_CLASS,
                    "The DIT content rule \"%s\" is associated with a " +
                    "structural objectclass %s that is not defined in the " +
                    "server schema.  A default objectclass will be created " +
                    "with this OID.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_STRUCTURAL_CLASS_NOT_STRUCTURAL,
                    "The DIT content rule \"%s\" is associated with the " +
                    "objectclass with OID %s (%s).  This objectclass exists " +
                    "in the server schema but is defined as %s rather than " +
                    "structural.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because it contained an " +
                    "illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS,
                    "The DIT content rule \"%s\" is associated with an " +
                    "auxiliary objectclass %s that is not defined in the " +
                    "server schema.  A default objectclass will be created " +
                    "with this OID.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY,
                    "The DIT content rule \"%s\" is associated with an " +
                    "auxiliary objectclass %s.  This objectclass exists " +
                    "in the server schema but is defined as %s rather than " +
                    "auxiliary.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR,
                    "The DIT content rule \"%s\" is associated with a " +
                    "required attribute type %s that is not defined in the " +
                    "server schema.  A default attribute type will be " +
                    "created with this name.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR,
                    "The DIT content rule \"%s\" is associated with an " +
                    "optional attribute type %s that is not defined in the " +
                    "server schema.  A default attribute type will be " +
                    "created with this name.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR,
                    "The DIT content rule \"%s\" is associated with a " +
                    "prohibited attribute type %s that is not defined in the " +
                    "server schema.  A default attribute type will be " +
                    "created with this name.");
    registerMessage(MSGID_ATTR_SYNTAX_DCR_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "content rule description because a single quote was " +
                    "expected at position %d but the %s character was found " +
                    "instead.");


    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid name " +
                    "form description because it was empty or contained only " +
                    "whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because an open parenthesis was " +
                    "expected at position %d but instead a '%s' character " +
                    "was found.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because the end of the value was " +
                    "encountered while the Directory Server expected more " +
                    "data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because the numeric OID contained two " +
                    "consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because the numeric OID contained an " +
                    "illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because the non-numeric OID contained " +
                    "an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because it contained an unexpected " +
                    "closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because it contained an illegal " +
                    "character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_UNKNOWN_STRUCTURAL_CLASS,
                    "The name form description \"%s\" is associated with a " +
                    "structural objectclass %s that is not defined in the " +
                    "server schema.  A default objectclass will be created " +
                    "with this OID.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_STRUCTURAL_CLASS_NOT_STRUCTURAL,
                    "The name form description \"%s\" is associated with the " +
                    "objectclass with OID %s (%s).  This objectclass exists " +
                    "in the server schema but is defined as %s rather than " +
                    "structural.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_UNKNOWN_REQUIRED_ATTR,
                    "The definition for the name form with OID %s declared " +
                    "that it should include required attribute \"%s\".  No " +
                    "attribute type matching this name or OID exists in the " +
                    "server schema, so a default attribute type with the " +
                    "directory string syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_UNKNOWN_OPTIONAL_ATTR,
                    "The definition for the name form with OID %s declared " +
                    "that it should include optional attribute \"%s\".  No " +
                    "attribute type matching this name or OID exists in the " +
                    "server schema, so a default attribute type with the " +
                    "directory string syntax will be used.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_NO_STRUCTURAL_CLASS,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because it does not specify the " +
                    "structural objectclass with which it is associated.");
    registerMessage(MSGID_ATTR_SYNTAX_NAME_FORM_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as a name " +
                    "form description because a single quote was expected at " +
                    "position %d but the %s character was found instead.");


    registerMessage(MSGID_ATTR_SYNTAX_MR_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid " +
                    "matching rule description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because an open parenthesis " +
                    "was expected at position %d but instead a '%s' " +
                    "character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because the end of the value " +
                    "was encountered while the Directory Server expected " +
                    "more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because the non-numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because it contained an " +
                    "illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_UNKNOWN_SYNTAX,
                    "The matching rule description \"%s\" is associated with " +
                    "attribute syntax %s that is not defined in the " +
                    "server schema.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_NO_SYNTAX,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because it does not specify " +
                    "the attribute syntax with which it is associated.");
    registerMessage(MSGID_ATTR_SYNTAX_MR_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because a single quote was " +
                    "expected at position %d but the %s character was found " +
                    "instead.");


    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid " +
                    "matching rule use description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because an open " +
                    "parenthesis was expected at position %d but instead a " +
                    "'%s' character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because the end of the " +
                    "value was encountered while the Directory Server " +
                    "expected more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because the non-numeric " +
                    "OID contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_UNKNOWN_MATCHING_RULE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because the specified " +
                    "matching rule %s is unknown.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because it contained an " +
                    "illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_UNKNOWN_ATTR,
                    "The matching rule use description \"%s\" is associated " +
                    "with attribute type %s that is not defined in the " +
                    "server schema.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_NO_ATTR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule description because it does not specify " +
                    "the set of attribute types that may be used with the " +
                    "associated OID.");
    registerMessage(MSGID_ATTR_SYNTAX_MRUSE_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as a " +
                    "matching rule use description because a single quote " +
                    "was expected at position %d but the %s character was " +
                    "found instead.");


    registerMessage(MSGID_ATTR_SYNTAX_DSR_EMPTY_VALUE,
                    "The provided value could not be parsed as a valid DIT " +
                    "structure rule description because it was empty or " +
                    "contained only whitespace.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_EXPECTED_OPEN_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because an open parenthesis " +
                    "was expected at position %d but instead a '%s' " +
                    "character was found.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_TRUNCATED_VALUE,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because the end of the value " +
                    "was encountered while the Directory Server expected " +
                    "more data to be provided.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR_IN_RULE_ID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because the rule ID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_UNEXPECTED_CLOSE_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because it contained an " +
                    "unexpected closing parenthesis at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because it contained an " +
                    "illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_UNKNOWN_NAME_FORM,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because it referenced an " +
                    "unknown name form %s.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because it referenced an " +
                    "unknown rule ID %d for a superior DIT structure rule.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_NO_NAME_FORM,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because it did not specify " +
                    "the name form for the rule.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_EXPECTED_QUOTE_AT_POS,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because a single quote was " +
                    "expected at position %d but the %s character was found " +
                    "instead.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_DOUBLE_PERIOD_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because the numeric OID " +
                    "contained two consecutive periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR_IN_NUMERIC_OID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because the numeric OID " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_DSR_ILLEGAL_CHAR_IN_STRING_OID,
                    "The provided value \"%s\" could not be parsed as a DIT " +
                    "structure rule description because the non-numeric OID " +
                    "contained an illegal character %s at position %d.");


    registerMessage(MSGID_ATTR_SYNTAX_TELEX_TOO_SHORT,
                    "The provided value \"%s\" is too short to be a valid " +
                    "telex number value.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEX_NOT_PRINTABLE,
                    "The provided value \"%s\" does not hold a valid telex " +
                    "number because a character %s at position %d was not " +
                    "a valid printable string character.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR,
                    "The provided value \"%s\" does not hold a valid telex " +
                    "number because character %s at position %d was neither " +
                    "a valid printable string character nor a dollar sign to " +
                    "separate the telex number components.");
    registerMessage(MSGID_ATTR_SYNTAX_TELEX_TRUNCATED,
                    "The provided value \"%s\" does not hold a valid telex " +
                    "number because the end of the value was found before " +
                    "three dollar-delimited printable strings could be read.");


    registerMessage(MSGID_ATTR_SYNTAX_FAXNUMBER_EMPTY,
                    "The provided value could not be parsed as a valid " +
                    "facsimile telephone number because it was empty.");
    registerMessage(MSGID_ATTR_SYNTAX_FAXNUMBER_NOT_PRINTABLE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid facsimile telephone number because character %s " +
                    "at position %d was not a valid printable string " +
                    "character.");
    registerMessage(MSGID_ATTR_SYNTAX_FAXNUMBER_END_WITH_DOLLAR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid facsimile telephone number because it ends with " +
                    "a dollar sign, but that dollar sign should have been " +
                    "followed by a fax parameter.");
    registerMessage(MSGID_ATTR_SYNTAX_FAXNUMBER_ILLEGAL_PARAMETER,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid facsimile telephone number because the string " +
                    "\"%s\" between positions %d and %d was not a valid fax " +
                    "parameter.");


    registerMessage(MSGID_ATTR_SYNTAX_NAMEANDUID_INVALID_DN,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid name and optional UID value because an error " +
                    "occurred while trying to parse the DN portion:  %s.");
    registerMessage(MSGID_ATTR_SYNTAX_NAMEANDUID_ILLEGAL_BINARY_DIGIT,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid name and optional UID value because the UID " +
                    "portion contained an illegal binary digit %s at " +
                    "position %d.");


    registerMessage(MSGID_ATTR_SYNTAX_TELETEXID_EMPTY,
                    "The provided value could not be parsed as a valid " +
                    "teletex terminal identifier because it was empty.");
    registerMessage(MSGID_ATTR_SYNTAX_TELETEXID_NOT_PRINTABLE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid teletex terminal identifier because character %s " +
                    "at position %d was not a valid printable string " +
                    "character.");
    registerMessage(MSGID_ATTR_SYNTAX_TELETEXID_END_WITH_DOLLAR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid teletex terminal identifier because it ends with " +
                    "a dollar sign, but that dollar sign should have been " +
                    "followed by a TTX parameter.");
    registerMessage(MSGID_ATTR_SYNTAX_TELETEXID_PARAM_NO_COLON,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid teletex terminal identifier because the parameter " +
                    "string does not contain a colon to separate the name " +
                    "from the value.");
    registerMessage(MSGID_ATTR_SYNTAX_TELETEXID_ILLEGAL_PARAMETER,
                    "The provided value \"%s\" could not be parsed as a " +
                    "valid teletex terminal identifier because the string " +
                    "\"%s\" is not a valid TTX parameter name.");


    registerMessage(MSGID_ATTR_SYNTAX_OTHER_MAILBOX_EMPTY_VALUE,
                    "The provided value could not be parsed as an other " +
                    "mailbox value because it was empty.");
    registerMessage(MSGID_ATTR_SYNTAX_OTHER_MAILBOX_NO_MBTYPE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "other mailbox value because there was no mailbox type " +
                    "before the dollar sign.");
    registerMessage(MSGID_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MBTYPE_CHAR,
                    "The provided value \"%s\" could not be parsed as an " +
                    "other mailbox value because the mailbox type contained " +
                    "an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_OTHER_MAILBOX_NO_MAILBOX,
                    "The provided value \"%s\" could not be parsed as an " +
                    "other mailbox value because there was no mailbox after " +
                    "the dollar sign.");
    registerMessage(MSGID_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MB_CHAR,
                    "The provided value \"%s\" could not be parsed as an " +
                    "other mailbox value because the mailbox contained an " +
                    "illegal character %s at position %d.");


    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_NO_OC,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because it did not contain an objectclass " +
                    "name or OID before the octothorpe (#) character.");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_ILLEGAL_CHAR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s contained " +
                    "an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_MISSING_CLOSE_PAREN,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s did not " +
                    "contain a close parenthesis that corresponded to the " +
                    "initial open parenthesis.");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_INVALID_QUESTION_MARK,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s started " +
                    "with a question mark but was not followed by the string " +
                    "\"true\" or \"false\".");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_NO_DOLLAR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s did not " +
                    "contain a dollar sign to separate the attribute type " +
                    "from the match type.");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_NO_ATTR,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s did not " +
                    "specify an attribute type before the dollar sign.");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_NO_MATCH_TYPE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s did not " +
                    "specify a match type after the dollar sign.");
    registerMessage(MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE,
                    "The provided value \"%s\" could not be parsed as a " +
                    "guide value because the criteria portion %s had an " +
                    "invalid match type starting at position %d.");


    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SHARP,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because it did not contain an " +
                    "octothorpe (#) character to separate the objectclass " +
                    "from the criteria.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_OC,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because it did not contain an " +
                    "objectclass name or OID before the octothorpe (#) " +
                    "character.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_DOUBLE_PERIOD_IN_OC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because the numeric OID %s " +
                    "specifying the objectclass contained two consecutive " +
                    "periods at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_ILLEGAL_CHAR_IN_OC_OID,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because the numeric OID %s " +
                    "specifying the objectclass contained an illegal " +
                    "character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_ILLEGAL_CHAR_IN_OC_NAME,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because the objectclass name %s " +
                    "contained an illegal character %s at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_FINAL_SHARP,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because it did not have an " +
                    "octothorpe (#) character to separate the criteria from " +
                    "the scope.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SCOPE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because no scope was provided " +
                    "after the final octothorpe (#) character.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_INVALID_SCOPE,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because the specified scope %s " +
                    "was invalid.");
    registerMessage(MSGID_ATTR_SYNTAX_ENHANCEDGUIDE_NO_CRITERIA,
                    "The provided value \"%s\" could not be parsed as an " +
                    "enhanced guide value because it did not specify any " +
                    "criteria between the octothorpe (#) characters.");


    registerMessage(MSGID_OMR_CASE_EXACT_COMPARE_CANNOT_NORMALIZE,
                    "An error occurred while attempting to compare two " +
                    "AttributeValue objects using the caseExactOrderingMatch " +
                    "matching rule because the normalized form of one of " +
                    "those values could not be retrieved:  %s.");
    registerMessage(MSGID_OMR_CASE_EXACT_COMPARE_INVALID_TYPE,
                    "An error occurred while attempting to compare two " +
                    "objects using the caseExactOrderingMatch matching rule " +
                    "because the objects were of an unsupported type %s.  " +
                    "Only byte arrays, ASN.1 octet strings, and attribute " +
                    "value objects may be compared.");


    registerMessage(MSGID_OMR_CASE_IGNORE_COMPARE_CANNOT_NORMALIZE,
                    "An error occurred while attempting to compare two " +
                    "AttributeValue objects using the " +
                    "caseIgnoreOrderingMatch matching rule because the " +
                    "normalized form of one of those values could not be " +
                    "retrieved:  %s.");
    registerMessage(MSGID_OMR_CASE_IGNORE_COMPARE_INVALID_TYPE,
                    "An error occurred while attempting to compare two " +
                    "objects using the caseIgnoreOrderingMatch matching rule " +
                    "because the objects were of an unsupported type %s.  " +
                    "Only byte arrays, ASN.1 octet strings, and attribute " +
                    "value objects may be compared.");


    registerMessage(MSGID_OMR_GENERALIZED_TIME_COMPARE_CANNOT_NORMALIZE,
                    "An error occurred while attempting to compare two " +
                    "AttributeValue objects using the " +
                    "generalizedTimeOrderingMatch matching rule because the " +
                    "normalized form of one of those values could not be " +
                    "retrieved:  %s.");
    registerMessage(MSGID_OMR_GENERALIZED_TIME_COMPARE_INVALID_TYPE,
                    "An error occurred while attempting to compare two " +
                    "objects using the generalizedTimeOrderingMatch matching " +
                    "rule because the objects were of an unsupported type " +
                    "%s.  Only byte arrays, ASN.1 octet strings, and " +
                    "attribute value objects may be compared.");


    registerMessage(MSGID_OMR_INTEGER_COMPARE_CANNOT_NORMALIZE,
                    "An error occurred while attempting to compare two " +
                    "AttributeValue objects using the integerOrderingMatch " +
                    "matching rule because the normalized form of one of " +
                    "those values could not be retrieved:  %s.");
    registerMessage(MSGID_OMR_INTEGER_COMPARE_INVALID_TYPE,
                    "An error occurred while attempting to compare two " +
                    "objects using the integerOrderingMatch matching rule " +
                    "because the objects were of an unsupported type %s.  " +
                    "Only byte arrays, ASN.1 octet strings, and attribute " +
                    "value objects may be compared.");


    registerMessage(MSGID_OMR_NUMERIC_STRING_COMPARE_CANNOT_NORMALIZE,
                    "An error occurred while attempting to compare two " +
                    "AttributeValue objects using the " +
                    "numericStringOrderingMatch matching rule because the " +
                    "normalized form of one of those values could not be " +
                    "retrieved:  %s.");
    registerMessage(MSGID_OMR_NUMERIC_STRING_COMPARE_INVALID_TYPE,
                    "An error occurred while attempting to compare two " +
                    "objects using the numericStringOrderingMatch matching " +
                    "rule because the objects were of an unsupported type " +
                    "%s.  Only byte arrays, ASN.1 octet strings, and " +
                    "attribute value objects may be compared.");


    registerMessage(MSGID_OMR_OCTET_STRING_COMPARE_CANNOT_NORMALIZE,
                    "An error occurred while attempting to compare two " +
                    "AttributeValue objects using the " +
                    "octetStringOrderingMatch matching rule because the " +
                    "normalized form of one of those values could not be " +
                    "retrieved:  %s.");
    registerMessage(MSGID_OMR_OCTET_STRING_COMPARE_INVALID_TYPE,
                    "An error occurred while attempting to compare two " +
                    "objects using the octetStringOrderingMatch matching " +
                    "rule because the objects were of an unsupported type " +
                    "%s.  Only byte arrays, ASN.1 octet strings, and " +
                    "attribute value objects may be compared.");


    registerMessage(MSGID_ATTR_SYNTAX_UUID_INVALID_LENGTH,
                    "The provided value \"%s\" has an invalid length for a " +
                    "UUID.  All UUID values must have a length of exactly 36 " +
                    "bytes, but the provided value had a length of %d bytes.");
    registerMessage(MSGID_ATTR_SYNTAX_UUID_EXPECTED_DASH,
                    "The provided value \"%s\" should have had a dash at " +
                    "position %d, but the character '%s' was found instead.");
    registerMessage(MSGID_ATTR_SYNTAX_UUID_EXPECTED_HEX,
                    "The provided value \"%s\" should have had a hexadecimal " +
                    "digit at position %d, but the character '%s' was found " +
                    "instead.");


    registerMessage(
         MSGID_ATTR_SYNTAX_DIRECTORYSTRING_DESCRIPTION_ALLOW_ZEROLENGTH,
         "Indicates whether attributes with the directory string syntax will " +
         "be allowed to have zero-length values.  This is technically not " +
         "allowed by the LDAP specifications, but it may be useful for " +
         "backward compatibility with previous Directory Server releases.");
    registerMessage(
         MSGID_ATTR_SYNTAX_DIRECTORYSTRING_CANNOT_DETERMINE_ZEROLENGTH,
         "An error occurred while trying to determine the value of the %s " +
         "configuration attribute, which indicates whether directory string " +
         "attributes should be allowed to have zero-length values:  %s.");
    registerMessage(MSGID_ATTR_SYNTAX_DIRECTORYSTRING_INVALID_ZEROLENGTH_VALUE,
                    "The operation attempted to assign a zero-length value " +
                    "to an attribute with the directory string syntax.");
    registerMessage(MSGID_ATTR_SYNTAX_DIRECTORYSTRING_UPDATED_ALLOW_ZEROLENGTH,
                    "The %s attribute in configuration entry %s has been " +
                    "updated with a new value of %s.");


    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_INVALID_SCHEME_CHAR,
                    "The provided authPassword value had an invalid scheme " +
                    "character at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_NO_SCHEME,
                    "The provided authPassword value had a zero-length " +
                    "scheme element.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_NO_SCHEME_SEPARATOR,
                    "The provided authPassword value was missing the " +
                    "separator character or had an illegal character between " +
                    "the scheme and authInfo elements.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_INFO_CHAR,
                    "The provided authPassword value had an invalid authInfo " +
                    "character at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO,
                    "The provided authPassword value had a zero-length " +
                    "authInfo element.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO_SEPARATOR,
                    "The provided authPassword value was missing the " +
                    "separator character or had an illegal character between " +
                    "the authInfo and authValue elements.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_VALUE_CHAR,
                    "The provided authPassword value had an invalid " +
                    "authValue character at position %d.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_NO_AUTH_VALUE,
                    "The provided authPassword value had a zero-length " +
                    "authValue element.");
    registerMessage(MSGID_ATTR_SYNTAX_AUTHPW_INVALID_TRAILING_CHAR,
                    "The provided authPassword value had an invalid trailing " +
                    "character at position %d.");


    registerMessage(MSGID_EMR_INTFIRSTCOMP_NO_INITIAL_PARENTHESIS,
                    "The provided value \"%s\" could not be parsed by the " +
                    "integer first component matching rule because it did " +
                    "not start with a parenthesis.");
    registerMessage(MSGID_EMR_INTFIRSTCOMP_NO_NONSPACE,
                    "The provided value \"%s\" could not be parsed by the " +
                    "integer first component matching rule because it did " +
                    "not have any non-space characters after the opening " +
                    "parenthesis.");
    registerMessage(MSGID_EMR_INTFIRSTCOMP_NO_SPACE_AFTER_INT,
                    "The provided value \"%s\" could not be parsed by the " +
                    "integer first component matching rule because it did " +
                    "not have any space characters after the first component.");
    registerMessage(MSGID_EMR_INTFIRSTCOMP_FIRST_COMPONENT_NOT_INT,
                    "The provided value \"%s\" could not be parsed by the " +
                    "integer first component matching rule because the first " +
                    "component does not appear to be an integer value.");


    registerMessage(MSGID_ATTR_SYNTAX_USERPW_NO_VALUE,
                    "No value was given to decode by the user password " +
                    "attribute syntax.");
    registerMessage(MSGID_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE,
                    "Unable to decode the provided value according to the " +
                    "user password syntax because the value does not start " +
                    "with the opening curly brace (\"{\") character.");
    registerMessage(MSGID_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE,
                    "Unable to decode the provided value according to the " +
                    "user password syntax because the value does not contain " +
                    "a closing curly brace (\"}\") character.");
    registerMessage(MSGID_ATTR_SYNTAX_USERPW_NO_SCHEME,
                    "Unable to decode the provided value according to the " +
                    "user password syntax because the value does not contain " +
                    "a storage scheme name.");

    registerMessage(MSGID_ATTR_SYNTAX_RFC3672_SUBTREE_SPECIFICATION_INVALID,
                    "The provided value \"%s\" could not be parsed as a" +
                    " valid RFC 3672 subtree specification.");

    registerMessage(MSGID_ATTR_SYNTAX_ABSOLUTE_SUBTREE_SPECIFICATION_INVALID,
                    "The provided value \"%s\" could not be parsed as a" +
                    " valid absolute subtree specification.");

    registerMessage(MSGID_ATTR_SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_INVALID,
                    "The provided value \"%s\" could not be parsed as a" +
                    " valid relative subtree specification.");
  }
}

