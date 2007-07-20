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



import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the general server utilities.
 */
public class UtilityMessages
{
  /**
   * The message ID for the message that will be used if an attempt is made to
   * base64-decode a string with a length that is not a multiple of four bytes.
   * This takes a single argument, which is the string to decode.
   */
  public static final int MSGID_BASE64_DECODE_INVALID_LENGTH =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 1;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * base64-decode a string containing a character that is not allowed in
   * base64-encoded values.  This takes two arguments, which are the value to
   * decode and the invalid character.
   */
  public static final int MSGID_BASE64_DECODE_INVALID_CHARACTER =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 2;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode a hex string with a length that is not a multiple of two bytes.
   * This takes a single argument, which is the string to decode.
   */
  public static final int MSGID_HEX_DECODE_INVALID_LENGTH =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 3;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode a hex string containing an invalid hexadecimal digit.  This takes
   * two arguments, which are the value to decode and the invalid character.
   */
  public static final int MSGID_HEX_DECODE_INVALID_CHARACTER =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 4;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but the first line started with a space.  This takes
   * two arguments, which are the line number and the contents of the line.
   */
  public static final int MSGID_LDIF_INVALID_LEADING_SPACE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 5;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but the first line started with a space.  This takes
   * two arguments, which are the starting line number for the entry and the
   * contents of the line with no attribute name.
   */
  public static final int MSGID_LDIF_NO_ATTR_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 6;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but the first line is not a DN.  This takes two
   * arguments, which are the starting line number for the entry and the
   * contents of the first line.
   */
  public static final int MSGID_LDIF_NO_DN =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 7;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but one of the lines contains an invalid separator
   * between the "dn" tag and the actual DN.  This takes two arguments, which
   * are the starting line number for the entry and the contents of the line
   * with the invalid separator.
   */
  public static final int MSGID_LDIF_INVALID_DN_SEPARATOR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 8;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry in which the DN cannot be decoded properly.  This takes
   * two arguments, which are the starting line number for the entry and the
   * contents of the line with the invalid separator.
   */
  public static final int MSGID_LDIF_INVALID_DN =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 9;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but one of the lines contains an invalid separator
   * between the attribute name tag and the value.  This takes three arguments,
   * which are the DN of the entry, the starting line number for the entry, and
   * the contents of the line with the invalid separator.
   */
  public static final int MSGID_LDIF_INVALID_ATTR_SEPARATOR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 10;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but the DN appears to be base64-encoded but it is not
   * possible to decode it.  This takes three arguments, which are the starting
   * line number for the entry, the invalid DN line, and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDIF_COULD_NOT_BASE64_DECODE_DN =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 11;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but an attribute appears to be base64-encoded but it is
   * not possible to decode it.  This takes four arguments, which are the DN of
   * the entry, the starting line number for the entry, the invalid attribute
   * line, and a string representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_COULD_NOT_BASE64_DECODE_ATTR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 12;



  /**
   * The message ID for the warning message that will be used if an entry is
   * read from an LDIF file that contains a duplicate objectclass value.  This
   * takes three arguments, which are the DN of the entry, the starting line
   * number for the entry, and the name of the duplicate objectclass.
   */
  public static final int MSGID_LDIF_DUPLICATE_OBJECTCLASS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_WARNING | 13;



  /**
   * The message ID for the warning message that will be used if an entry is
   * read from an LDIF file that contains a duplicate attribute value.  This
   * takes four arguments, which are the DN of the entry, the starting line
   * number for the entry, the name of the attribute, and the duplicate value.
   */
  public static final int MSGID_LDIF_DUPLICATE_ATTR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_WARNING | 14;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but multiple values are provided for a single-valued
   * attribute.  This takes three arguments, which are the DN of the entry, the
   * starting line number for the entry, and the name of the attribute.
   */
  public static final int MSGID_LDIF_MULTIPLE_VALUES_FOR_SINGLE_VALUED_ATTR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 15;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but that entry contains an attribute value with an
   * illegal syntax.  This takes five arguments, which are the DN of the entry,
   * the starting line number for the entry, the illegal value, the name of the
   * attribute, and a string representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_INVALID_ATTR_SYNTAX =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 16;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but that entry does not conform to the server's schema
   * configuration.  This takes three arguments, which are the DN of the entry,
   * the starting line number for the entry, and a message that explains the
   * schema violation.
   */
  public static final int MSGID_LDIF_SCHEMA_VIOLATION =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 17;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * write an LDIF file but the specified file already exists.  This takes a
   * single argument, which is the name of the file.
   */
  public static final int MSGID_LDIF_FILE_EXISTS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 18;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read the content of an attribute from a specified URL but that URL was
   * invalid.  This takes four arguments, which are the DN of the entry, the
   * line number on which that entry starts, the name of the attribute, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_INVALID_URL =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 19;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * read the content of an attribute from a specified URL but an I/O problem
   * occurred while attempting to read the information.  This takes five
   * arguments, which are the DN of the entry, the line number on which that
   * entry starts, the name of the attribute, the URL to retrieve, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_URL_IO_ERROR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 20;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * write a reject file but the specified file already exists.  This takes a
   * single argument, which is the name of the file.
   */
  public static final int MSGID_REJECT_FILE_EXISTS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 21;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine if an entry matches the set of filters that should be
   * used to include it in or exclude it from the import.  This takes three
   * arguments, which are the DN of the entry being imported, the line number on
   * which that entry starts in the LDIF source, and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_IMPORT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 22;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine if an entry matches the set of filters that should be
   * used to include it in or exclude it from the export.  This takes two
   * arguments, which are the DN of the entry being imported and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_EXPORT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 23;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there are invalid
   * attributes for a delete operation.
   */
  public static final int MSGID_LDIF_INVALID_DELETE_ATTRIBUTES =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 24;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there are no
   * attributes specified for a moddn operation.
   */
  public static final int MSGID_LDIF_NO_MOD_DN_ATTRIBUTES =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 25;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there is no delete old
   * RDN attribute specified for a moddn operation.
   */
  public static final int MSGID_LDIF_NO_DELETE_OLDRDN_ATTRIBUTE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 26;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there is an invalid value
   * for the delete old RDN attribute specified for a moddn operation.
   */
  public static final int MSGID_LDIF_INVALID_DELETE_OLDRDN_ATTRIBUTE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 27;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there is an invalid
   * attribute specified for a moddn operation.
   */
  public static final int MSGID_LDIF_INVALID_CHANGERECORD_ATTRIBUTE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 28;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there is an invalid
   * attribute specified for a modify operation.
   */
  public static final int MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 29;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there is an invalid
   * value specified for the changetype operation.
   */
  public static final int MSGID_LDIF_INVALID_CHANGETYPE_ATTRIBUTE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 30;

  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to parse an LDIF change record entry and there is an invalid
   * attribute value specified for a modify operation.
   */
  public static final int MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE_VAL =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 31;



  /**
   * The message ID for the message that will be used if a schema element name
   * or OID is invalid because it was null or empty.  This does not take any
   * arguments.
   */
  public static final int MSGID_SCHEMANAME_EMPTY_VALUE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 32;



  /**
   * The message ID for the message that will be used if a schema element name
   * or OID is invalid because it contains an illegal character.  This takes
   * three arguments, which are the provided value, the illegal character, and
   * the position of that illegal character.
   */
  public static final int MSGID_SCHEMANAME_ILLEGAL_CHAR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 33;



  /**
   * The message ID for the message that will be used if a schema element name
   * or OID is invalid because it contains consecutive periods.  This takes two
   * arguments, which are the provided value and the position of the consecutive
   * period.
   */
  public static final int MSGID_SCHEMANAME_CONSECUTIVE_PERIODS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 34;



  /**
   * The message ID for the message that will be used if an argument does not
   * have either a short or long identifier.  This takes a single argument,
   * which is the name of the argument.
   */
  public static final int MSGID_ARG_NO_IDENTIFIER =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 35;



  /**
   * The message ID for the message that will be used if an argument takes a
   * value but no value placeholder has been defined.  This takes a single
   * argument, which is the name of the argument.
   */
  public static final int MSGID_ARG_NO_VALUE_PLACEHOLDER =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 36;



  /**
   * The message ID for the message that will be used if an argument does not
   * have any value to decode as an integer.  This takes a single argument,
   * which is the name of the argument.
   */
  public static final int MSGID_ARG_NO_INT_VALUE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 37;



  /**
   * The message ID for the message that will be used if an argument value
   * cannot be decoded as an integer.  This takes two arguments, which are the
   * provided value and the name of the argument.
   */
  public static final int MSGID_ARG_CANNOT_DECODE_AS_INT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 38;



  /**
   * The message ID for the message that will be used if an argument has
   * multiple values that cannot be decoded as a single integer.  This takes a
   * single argument, which is the name of the argument.
   */
  public static final int MSGID_ARG_INT_MULTIPLE_VALUES =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 39;



  /**
   * The message ID for the message that will be used if an argument does not
   * have any value to decode as a Boolean.  This takes a single argument,
   * which is the name of the argument.
   */
  public static final int MSGID_ARG_NO_BOOLEAN_VALUE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 40;



  /**
   * The message ID for the message that will be used if an argument value
   * cannot be decoded as a Boolean.  This takes two arguments, which are the
   * provided value and the name of the argument.
   */
  public static final int MSGID_ARG_CANNOT_DECODE_AS_BOOLEAN =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 41;



  /**
   * The message ID for the message that will be used if an argument has
   * multiple values that cannot be decoded as a single Boolean.  This takes a
   * single argument, which is the name of the argument.
   */
  public static final int MSGID_ARG_BOOLEAN_MULTIPLE_VALUES =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 42;



  /**
   * The message ID for the message that will be used if an integer argument is
   * configured with both lower and upper bounds but the lower bound is greater
   * than the upper bound.  This takes three arguments, which are the name of
   * the argument, the lower bound, and the upper bound.
   */
  public static final int MSGID_INTARG_LOWER_BOUND_ABOVE_UPPER_BOUND =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 43;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign a value to an integer argument that is below the lower bound.  This
   * takes three arguments, which are the name of the argument, the provided
   * value, and the lower bound.
   */
  public static final int MSGID_INTARG_VALUE_BELOW_LOWER_BOUND =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 44;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign a value to an integer argument that is above the upper bound.  This
   * takes three arguments, which are the name of the argument, the provided
   * value, and the upper bound.
   */
  public static final int MSGID_INTARG_VALUE_ABOVE_UPPER_BOUND =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 45;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign a value to a Boolean argument.  This takes a single argument, which
   * is the name of the argument.
   */
  public static final int MSGID_BOOLEANARG_NO_VALUE_ALLOWED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 46;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign a value to a multi-choice argument that is not in the set of allowed
   * values.  This takes two arguments, which are the name of the argument and
   * the provided value.
   */
  public static final int MSGID_MCARG_VALUE_NOT_ALLOWED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 47;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * target a file that does not exist.  This takes two arguments, which are
   * the path to the file and the name of the argument.
   */
  public static final int MSGID_FILEARG_NO_SUCH_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 48;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether a file exists.  This takes three arguments,
   * which are the path to the file, the name of the argument, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FILEARG_CANNOT_VERIFY_FILE_EXISTENCE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 49;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying open a file for reading.  This takes three arguments, which are the
   * path to the file, the name of the argument, and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_FILEARG_CANNOT_OPEN_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 50;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying read a line from the file.  This takes three arguments, which are
   * the path to the file, the name of the argument, and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_FILEARG_CANNOT_READ_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 51;



  /**
   * The message ID for the message that will be used if the specified file is
   * empty.  This takes two arguments, which are the path to the file and the
   * name of the argument.
   */
  public static final int MSGID_FILEARG_EMPTY_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 52;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * define an argument with a short identifier that conflicts with an existing
   * argument.  This takes three arguments, which are the name of the rejected
   * argument, the conflicting short identifier, and the name of the existing
   * argument.
   */
  public static final int MSGID_ARGPARSER_DUPLICATE_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 53;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * define an argument with a long identifier that conflicts with an existing
   * argument.  This takes three arguments, which are the name of the rejected
   * argument, the conflicting long identifier, and the name of the existing
   * argument.
   */
  public static final int MSGID_ARGPARSER_DUPLICATE_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 54;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to read alternate default values from a properties file.  This takes
   * two arguments, which are the name of the properties file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_ARGPARSER_CANNOT_READ_PROPERTIES_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 55;



  /**
   * The message ID for the message that will be used if too many unnamed
   * trailing arguments are provided.  This takes a single argument, which is
   * the maximum number of trailing arguments allowed.
   */
  public static final int MSGID_ARGPARSER_TOO_MANY_TRAILING_ARGS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 56;



  /**
   * The message ID for the message that will be used if a long argument is
   * provided without including the argument name (i.e., starts with "--=").
   * This takes a single argument, which is the provided long argument.
   */
  public static final int MSGID_ARGPARSER_LONG_ARG_WITHOUT_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 57;



  /**
   * The message ID for the message that will be used if a long argument is
   * specified with a name that has not been registered with the parser.  This
   * takes a single argument, which is the name of the invalid long argument.
   */
  public static final int MSGID_ARGPARSER_NO_ARGUMENT_WITH_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 58;



  /**
   * The message ID for the message that will be used if a long argument
   * requires a value but none was provided.  This takes a single argument,
   * which is the name of the provided long argument.
   */
  public static final int MSGID_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 59;



  /**
   * The message ID for the message that will be used if a value provided for a
   * long argument was unacceptable.  This takes three arguments, which are the
   * rejected value, the name of the long argument, and the reason the value was
   * rejected.
   */
  public static final int MSGID_ARGPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 60;



  /**
   * The message ID for the message that will be used if multiple values were
   * provided for a long argument.  This takes a single argument, which is the
   * name of the long argument.
   */
  public static final int MSGID_ARGPARSER_NOT_MULTIVALUED_FOR_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 61;



  /**
   * The message ID for the message that will be used if a long argument does
   * not take a value but one was provided for it.  This takes a single
   * argument, which is the name of the long argument.
   */
  public static final int MSGID_ARGPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 62;



  /**
   * The message ID for the message that will be used if the "-" character by
   * itself is used as an argument.  This does not take any arguments.
   */
  public static final int MSGID_ARGPARSER_INVALID_DASH_AS_ARGUMENT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if a short argument is
   * specified with a name that has not been registered with the parser.  This
   * takes a single argument, which is the name of the invalid short argument.
   */
  public static final int MSGID_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 64;



  /**
   * The message ID for the message that will be used if a short argument
   * requires a value but none was provided.  This takes a single argument,
   * which is the name of the provided short argument.
   */
  public static final int MSGID_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 65;



  /**
   * The message ID for the message that will be used if a value provided for a
   * short argument was unacceptable.  This takes three arguments, which are the
   * rejected value, the name of the short argument, and the reason the value
   * was rejected.
   */
  public static final int MSGID_ARGPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 66;



  /**
   * The message ID for the message that will be used if multiple values were
   * provided for a short argument.  This takes a single argument, which is the
   * name of the short argument.
   */
  public static final int MSGID_ARGPARSER_NOT_MULTIVALUED_FOR_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 67;



  /**
   * The message ID for the message that will be used if a series of short
   * arguments are provided in a single block where one of the characters other
   * than the first is associated with an argument that requires a value.  This
   * takes three arguments, which are the string representation of the argument
   * character at the beginning of the block, the string of characters other
   * than the first character in that block, and the string representation of
   * the character that is associated with an argument that takes a value.
   */
  public static final int MSGID_ARGPARSER_CANT_MIX_ARGS_WITH_VALUES =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 68;



  /**
   * The message ID for the message that will be used if an unnamed trailing
   * argument is provided but none are allowed.  This takes a single argument,
   * which is the provided trailing argument.
   */
  public static final int MSGID_ARGPARSER_DISALLOWED_TRAILING_ARGUMENT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 69;



  /**
   * The message ID for the message that will be used if too few trailing
   * arguments were provided.  This takes a single argument, which is the
   * minimum number of trailing arguments that must be provided.
   */
  public static final int MSGID_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 70;



  /**
   * The message ID for the message that will be used if a required argument is
   * not assigned a value.  This takes a single argument, which is the name of
   * the missing required argument.
   */
  public static final int MSGID_ARGPARSER_NO_VALUE_FOR_REQUIRED_ARG =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 71;



  /**
   * The message ID for the message that will be used an attempt to move a file
   * fails because the file to move does not exist.  This takes a single
   * argument, which is the provided path of the file to move.
   */
  public static final int MSGID_MOVEFILE_NO_SUCH_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 72;



  /**
   * The message ID for the message that will be used an attempt to move a file
   * fails because the file to move exists but is not a file.  This takes a
   * single argument, which is the provided path of the file to move.
   */
  public static final int MSGID_MOVEFILE_NOT_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 73;



  /**
   * The message ID for the message that will be used an attempt to move a file
   * fails because the target directory does not exist.  This takes a single
   * argument, which is the provided path of the target directory.
   */
  public static final int MSGID_MOVEFILE_NO_SUCH_DIRECTORY =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 74;



  /**
   * The message ID for the message that will be used an attempt to move a file
   * fails because the target directory exists but is not a directory.  This
   * takes a single argument, which is the provided path of the target
   * directory.
   */
  public static final int MSGID_MOVEFILE_NOT_DIRECTORY =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 75;



  /**
   * The message ID for the message that will be used if a sender address for an
   * e-mail message is invalid for some reason.  This takes two arguments, which
   * are the invalid address and a message explaining why the address is
   * invalid.
   */
  public static final int MSGID_EMAILMSG_INVALID_SENDER_ADDRESS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 76;



  /**
   * The message ID for the message that will be used if a recipient address for
   * an e-mail message is invalid for some reason.  This takes two arguments,
   * which are the invalid address and a message explaining why the address is
   * invalid.
   */
  public static final int MSGID_EMAILMSG_INVALID_RECIPIENT_ADDRESS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 77;



  /**
   * The message ID for the message that will be used if an error message
   * cannot be sent to any of the configured mail servers.
   */
  public static final int MSGID_EMAILMSG_CANNOT_SEND =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 78;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * define a new subcommand with a name that conflicts with the name of another
   * subcommand already associated with the given argument parser.  This takes
   * a single argument, which is the subcommand name.
   */
  public static final int MSGID_ARG_SUBCOMMAND_DUPLICATE_SUBCOMMAND =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 79;



  /**
   * The message ID for the message that will be used if a subcommand has
   * multiple arguments with the same name.  This takes two arguments, which are
   * the name of the subcommand and the name of the argument.
   */
  public static final int MSGID_ARG_SUBCOMMAND_DUPLICATE_ARGUMENT_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 80;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an argument to a subcommand with a name that conflicts with the name of
   * a global argument.  This takes two arguments, which are the argument name
   * and the subcommand name.
   */
  public static final int MSGID_ARG_SUBCOMMAND_ARGUMENT_GLOBAL_CONFLICT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 81;



  /**
   * The message ID for the message that will be used if a subcommand has
   * multiple arguments with the same short ID.  This takes four arguments,
   * which are the name of the new argument being added, the name of the
   * subcommand, the string representation of the short identifier, and the
   * name of the existing argument with the same short ID.
   */
  public static final int MSGID_ARG_SUBCOMMAND_DUPLICATE_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 82;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an argument to a subcommand with a short ID that conflicts with the
   * short ID of a global argument.  This takes four arguments, which are the
   * argument name, the subcommand name, a string representation of the short ID
   * character, and the name of the conflicting global argument.
   */
  public static final int
       MSGID_ARG_SUBCOMMAND_ARGUMENT_SHORT_ID_GLOBAL_CONFLICT =
            CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 83;



  /**
   * The message ID for the message that will be used if a subcommand has
   * multiple arguments with the same long ID.  This takes four arguments,
   * which are the name of the new argument being added, the name of the
   * subcommand, the long identifier, and the name of the existing argument with
   * the same long ID.
   */
  public static final int MSGID_ARG_SUBCOMMAND_DUPLICATE_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 84;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an argument to a subcommand with a long ID that conflicts with the long
   * ID of a global argument.  This takes four arguments, which are the
   * argument name, the subcommand name, the long ID string, and the name of the
   * conflicting global argument.
   */
  public static final int
       MSGID_ARG_SUBCOMMAND_ARGUMENT_LONG_ID_GLOBAL_CONFLICT =
            CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 85;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a global argument with a name that conflicts with that of another
   * global argument.  This takes a single argument, which is the name of the
   * global argument.
   */
  public static final int MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 86;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a global argument with a name that conflicts with the name of a
   * subcommand argument.  This takes two arguments, which are the name of the
   * global argument and the name of the subcommand.
   */
  public static final int MSGID_SUBCMDPARSER_GLOBAL_ARG_NAME_SUBCMD_CONFLICT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 87;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a global argument with a short ID that conflicts with that of another
   * global argument.  This takes three arguments, which are the string
   * representation of the short ID character, the name of the new global
   * argument, and the name of the existing global argument with the same short
   * ID.
   */
  public static final int MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 88;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a global argument with a short ID that conflicts with that of a
   * subcommand argument.  This takes four arguments, which are the string
   * representation of the short ID character, the name of the new global
   * argument, the name of the conflicting argument, and the name of the
   * associated subcommand.
   */
  public static final int MSGID_SUBCMDPARSER_GLOBAL_ARG_SHORT_ID_CONFLICT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 89;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a global argument with a long ID that conflicts with that of another
   * global argument.  This takes three arguments, which are the long ID string,
   * the name of the new global argument, and the name of the existing global
   * argument with the same long ID.
   */
  public static final int MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 90;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a global argument with a long ID that conflicts with that of a
   * subcommand argument.  This takes four arguments, which are the long ID
   * string, the name of the new global argument, the name of the conflicting
   * argument, and the name of the associated subcommand.
   */
  public static final int MSGID_SUBCMDPARSER_GLOBAL_ARG_LONG_ID_CONFLICT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 91;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read the specified properties file.  This takes two
   * arguments, which are the path to the properties file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SUBCMDPARSER_CANNOT_READ_PROPERTIES_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 92;



  /**
   * The message ID for the message that will be used if a command-line argument
   * is provided without a name.  This takes a single argument, which is the
   * provided argument string.
   */
  public static final int MSGID_SUBCMDPARSER_LONG_ARG_WITHOUT_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 93;



  /**
   * The message ID for the message that will be used if a command-line argument
   * is provided with a long ID that does not map to a valid global argument.
   * This takes one argument, which is the provided long argument ID string.
   */
  public static final int MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 94;



  /**
   * The message ID for the message that will be used if a command-line argument
   * is provided with a long ID that does not map to a valid global or
   * subcommand argument.  This takes one argument, which is the provided
   * argument long ID string.
   */
  public static final int MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 95;



  /**
   * The message ID for the message that will be used if a command-line argument
   * is used which requires a value but for which none was provided.  This takes
   * a single argument, which is the long identifier string for the argument.
   */
  public static final int
       MSGID_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID =
            CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 96;



  /**
   * The message ID for the message that will be used if a value provided for a
   * long argument was unacceptable.  This takes three arguments, which are the
   * rejected value, the name of the long argument, and the reason the value was
   * rejected.
   */
  public static final int MSGID_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 97;



  /**
   * The message ID for the message that will be used if multiple values were
   * provided for a long argument.  This takes a single argument, which is the
   * name of the long argument.
   */
  public static final int MSGID_SUBCMDPARSER_NOT_MULTIVALUED_FOR_LONG_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 98;



  /**
   * The message ID for the message that will be used if a long argument does
   * not take a value but one was provided for it.  This takes a single
   * argument, which is the name of the long argument.
   */
  public static final int MSGID_SUBCMDPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 99;



  /**
   * The message ID for the message that will be used if the "-" character by
   * itself is used as an argument.  This does not take any arguments.
   */
  public static final int MSGID_SUBCMDPARSER_INVALID_DASH_AS_ARGUMENT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 100;



  /**
   * The message ID for the message that will be used if a command-line argument
   * is provided with a short ID that does not map to a valid global argument.
   * This takes one argument, which is a string representation of the provided
   * short argument ID character.
   */
  public static final int MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 101;



  /**
   * The message ID for the message that will be used if a command-line argument
   * is provided with a short ID that does not map to a valid global or
   * subcommand argument.  This takes one argument, which is a string
   * representation of the provided short argument ID character.
   */
  public static final int MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 102;



  /**
   * The message ID for the message that will be used if a short argument
   * requires a value but none was provided.  This takes a single argument,
   * which is the name of the provided short argument.
   */
  public static final int
       MSGID_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID =
            CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 103;



  /**
   * The message ID for the message that will be used if a value provided for a
   * short argument was unacceptable.  This takes three arguments, which are the
   * rejected value, the name of the short argument, and the reason the value
   * was rejected.
   */
  public static final int MSGID_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 104;



  /**
   * The message ID for the message that will be used if multiple values were
   * provided for a short argument.  This takes a single argument, which is the
   * name of the short argument.
   */
  public static final int MSGID_SUBCMDPARSER_NOT_MULTIVALUED_FOR_SHORT_ID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 105;



  /**
   * The message ID for the message that will be used if a series of short
   * arguments are provided in a single block where one of the characters other
   * than the first is associated with an argument that requires a value.  This
   * takes three arguments, which are the string representation of the argument
   * character at the beginning of the block, the string of characters other
   * than the first character in that block, and the string representation of
   * the character that is associated with an argument that takes a value.
   */
  public static final int MSGID_SUBCMDPARSER_CANT_MIX_ARGS_WITH_VALUES =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 106;



  /**
   * The message ID for the message that will be used if a provided argument
   * does not start with one or two dashes and is not the name of a valid
   * subcommand.  This takes a single argument, which is the provided
   * command-line argument.
   */
  public static final int MSGID_SUBCMDPARSER_INVALID_ARGUMENT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 107;



  /**
   * The message ID for the message that will be used if a multiple subcommands
   * were provided.  This takes two arguments, which are the name of the new
   * new subcommand that was encountered and the name of the subcommand that had
   * previously been identified.
   */
  public static final int MSGID_SUBCMDPARSER_MULTIPLE_SUBCOMMANDS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 108;



  /**
   * The message ID for the message that will be used if a required argument is
   * not assigned a value.  This takes a single argument, which is the name of
   * the missing required argument.
   */
  public static final int MSGID_SUBCMDPARSER_NO_VALUE_FOR_REQUIRED_ARG =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 109;



  /**
   * The message ID for the message that will be used if an LDAP URL doesn't
   * contain a "://" after the scheme.  It takes a single argument, which is the
   * provided URL string.
   */
  public static final int MSGID_LDAPURL_NO_COLON_SLASH_SLASH =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 110;



  /**
   * The message ID for the message that will be used if an LDAP URL doesn't
   * contain a scheme.  It takes a single argument, which is the provided URL
   * string.
   */
  public static final int MSGID_LDAPURL_NO_SCHEME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 111;



  /**
   * The message ID for the message that will be used if an LDAP URL doesn't
   * contain a host before the colon and port.  It takes a single argument,
   * which is the provided URL string.
   */
  public static final int MSGID_LDAPURL_NO_HOST =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 112;



  /**
   * The message ID for the message that will be used if an LDAP URL doesn't
   * contain a port after the host and colon.  It takes a single argument,
   * which is the provided URL string.
   */
  public static final int MSGID_LDAPURL_NO_PORT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 113;



  /**
   * The message ID for the message that will be used if an error occurred while
   * attempting to decode the port.  This takes two arguments, which are the
   * provided URL string and the host string.
   */
  public static final int MSGID_LDAPURL_CANNOT_DECODE_PORT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 114;



  /**
   * The message ID for the message that will be used if the port in an LDAP URL
   * is outside the valid range.  This takes two arguments, which are the
   * provided URL string and the decoded port number.
   */
  public static final int MSGID_LDAPURL_INVALID_PORT =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 115;



  /**
   * The message ID for the message that will be used if an LDAP URL has an
   * invalid scope.  This takes two arguments, which are the provided URL string
   * and the invalid scope string.
   */
  public static final int MSGID_LDAPURL_INVALID_SCOPE_STRING =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 116;



  /**
   * The message ID for the message that will be used if an LDAP URL portion
   * cannot be decoded because a percent sign is not followed by two hex digits.
   * This takes two arguments, which are the provided URL portion and the
   * position of the percent sign.
   */
  public static final int MSGID_LDAPURL_PERCENT_TOO_CLOSE_TO_END =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 117;



  /**
   * The message ID for the message that will be used if an LDAP URL portion
   * cannot be decoded because one of the two bytes following a percent sign is
   * not a valid hex character.  This takes two arguments, which are the
   * provided URL portion and the position of the illegal hex digit.
   */
  public static final int MSGID_LDAPURL_INVALID_HEX_BYTE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 118;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create a UTF-8 string from a byte array.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAPURL_CANNOT_CREATE_UTF8_STRING =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 119;



  /**
   * The message ID for the message that will be used if a value cannot be
   * decoded as a named character set because it does not have a colon.  This
   * takes a single argument, which is the value string.
   */
  public static final int MSGID_CHARSET_NO_COLON =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 120;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * create a named character set without a name.  This does not take any
   * arguments.
   */
  public static final int MSGID_CHARSET_CONSTRUCTOR_NO_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 121;



  /**
   * The message ID for the message that will be used if a character set name
   * has an illegal character.  This takes two arguments, which are the
   * provided character set name and the position of the illegal character.
   */
  public static final int MSGID_CHARSET_CONSTRUCTOR_INVALID_NAME_CHAR =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 122;



  /**
   * The message ID for the message that will be used if a value cannot be
   * decoded as a named character set because it does not have a name.  This
   * takes a single argument, which is the value string.
   */
  public static final int MSGID_CHARSET_NO_NAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 123;



  /**
   * The message ID for the message that will be used if a value cannot be
   * decoded as a named character set because it does not have any characters
   * for the character set.  This takes a single argument, which is the value
   * string.
   */
  public static final int MSGID_CHARSET_NO_CHARS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 124;



  /**
   * The message ID for the message that will be used to express a length of
   * time in seconds.  This takes a single argument, which is the number of
   * seconds.
   */
  public static final int MSGID_TIME_IN_SECONDS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 125;



  /**
   * The message ID for the message that will be used to express a length of
   * time in minutes and seconds.  This takes two arguments, which are the
   * number of minutes and seconds.
   */
  public static final int MSGID_TIME_IN_MINUTES_SECONDS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 126;



  /**
   * The message ID for the message that will be used to express a length of
   * time in hours, minutes, and seconds.  This takes three arguments, which are
   * the number of hours, minutes, and seconds.
   */
  public static final int MSGID_TIME_IN_HOURS_MINUTES_SECONDS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 127;



  /**
   * The message ID for the message that will be used to express a length of
   * time in days, hours, minutes, and seconds.  This takes four arguments,
   * which is are number of days, hours, minutes, and seconds.
   */
  public static final int MSGID_TIME_IN_DAYS_HOURS_MINUTES_SECONDS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 128;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the account has been
   * temporarily locked.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 129;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the account has been
   * permanently locked.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 130;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the account has been
   * unlocked by an administrator.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_UNLOCKED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 131;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that an authentication
   * attempt failed because the account had been idle for too long.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 132;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that an authentication
   * attempt failed because the password had been administratively reset but not
   * changed by the user in the necessary window.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 133;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the account has been
   * administratively disabled.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_DISABLED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 134;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the account has been
   * administratively re-enabled.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_ENABLED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 135;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that an authentication
   * attempt failed because the account had expired.
   */
  public static final int MSGID_ACCTNOTTYPE_ACCOUNT_EXPIRED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 136;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that an authentication
   * attempt failed because the user's password had expired.
   */
  public static final int MSGID_ACCTNOTTYPE_PASSWORD_EXPIRED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 137;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the user's password will
   * expire in the near future.
   */
  public static final int MSGID_ACCTNOTTYPE_PASSWORD_EXPIRING =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 138;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the user's password was
   * reset by an administrator.
   */
  public static final int MSGID_ACCTNOTTYPE_PASSWORD_RESET =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 139;



  /**
   * The message ID for the message that will be used as the identifier string
   * for an account status notification indicating that the user's password was
   * changed by the user.
   */
  public static final int MSGID_ACCTNOTTYPE_PASSWORD_CHANGED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 140;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * set the permissions of a file that does not exist.  This takes a single
   * argument, which is the path to the specified file.
   */
  public static final int MSGID_FILEPERM_SET_NO_SUCH_FILE =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 141;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to execute the chmod command.  This takes two arguments, which are
   * the path to the file and a message explaining the problem that occurred.
   */
  public static final int MSGID_FILEPERM_CANNOT_EXEC_CHMOD =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 142;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * while attempting to update file permissions.  This takes a single argument,
   * which is the path to the file being updated.
   */
  public static final int MSGID_FILEPERM_SET_JAVA_EXCEPTION =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 143;



  /**
   * The message ID for the message that will be used if at least one attempt
   * to update file permissions failed, but at least one attempt was successful.
   * This takes a single argument, which is the path to the file being updated.
   */
  public static final int MSGID_FILEPERM_SET_JAVA_FAILED_ALTERED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 144;



  /**
   * The message ID for the message that will be used if all attempts to update
   * file permissions failed.  This takes a single argument, which is the path
   * to the file being updated.
   */
  public static final int MSGID_FILEPERM_SET_JAVA_FAILED_UNALTERED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 145;



  /**
   * The message ID for the message that will be used if an invalid UNIX mode
   * string is provided.  This takes a single argument, which is the provided
   * mode string.
   */
  public static final int MSGID_FILEPERM_INVALID_UNIX_MODE_STRING =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 146;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * use the exec method when the server has been configured to disallow that
   * capability.
   */
  public static final int MSGID_EXEC_DISABLED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 147;


  /**
   * The message ID for the message that will be used if a Validator check
   * fails.  This signals that there is a method invocation that violates
   * the pre-conditions of the method.
   */
  public static final int MSGID_VALIDATOR_PRECONDITION_NOT_MET =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 148;

  /**
   * The message ID for the message that will be used as the description of the
   * Global option.  This does not take any arguments.
   */
  public static final int MSGID_GLOBAL_OPTIONS =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 149;

  /**
   * The message ID for the message that will be used as the description of the
   * Global option reference.  This does take one argument.
   */
  public static final int MSGID_GLOBAL_OPTIONS_REFERENCE =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 150;

  /**
   * The message ID for the message that will be used as the description of the
   * Global option reference.  This does take 2 arguments.
   */
  public static final int MSGID_SUBCMD_OPTIONS =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 151;

  /**
   * The message ID for the message that will be used as the usage
   * prefix. This does not take any arguments.
   */
  public static final int MSGID_ARGPARSER_USAGE =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 152;

  /**
   * The message ID for the message that will be used as the heading
   * for the table of sub-commands. This does not take any arguments.
   */
  public static final int MSGID_SUBCMDPARSER_SUBCMD_HEADING =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 153;

  /**
   * The message ID for the message that will be used as the reference
   * to a --help-xxx argument which can be used to obtain usage
   * information on a specific subset of sub-commands. This takes a
   * single argument which is the name of the application.
   */
  public static final int MSGID_SUBCMDPARSER_SUBCMD_REFERENCE =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 154;

  /**
   * The message ID for the message that will be used as the heading
   * for the table of global arguments. This does not take any
   * arguments.
   */
  public static final int MSGID_SUBCMDPARSER_GLOBAL_HEADING =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 155;

  /**
   * The message ID for the message that will be used as the description of the
   * Global option reference.  This does take one argument.
   */
  public static final int MSGID_GLOBAL_HELP_REFERENCE =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 156;


  /**
   * The message ID for the message that will be used an attempt to rename a
   * file fails because the existing target file cannot be deleted.  This takes
   * a single argument, which is the provided path of the file to move.
   */
  public static final int MSGID_RENAMEFILE_CANNOT_RENAME =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 157;

  /**
   * The message ID for the message that will be used an attempt to rename a
   * file fails because the rename operation fails.  This takes two
   * arguments, the source and target file names.
   */
  public static final int MSGID_RENAMEFILE_CANNOT_DELETE_TARGET =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 158;

  /**
   * The message ID for the message that will be used if a client certificate is
   * rejected because it is expired.  This takes two arguments, which are the
   * subject DN of the certificate and a string representation of the notAfter
   * date.
   */
  public static final int MSGID_EXPCHECK_TRUSTMGR_CLIENT_CERT_EXPIRED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 159;



  /**
   * The message ID for the message that will be used if a client certificate is
   * rejected because it is not yet valid.  This takes two arguments, which are
   * the subject DN of the certificate and a string representation of the
   * notBefore date.
   */
  public static final int MSGID_EXPCHECK_TRUSTMGR_CLIENT_CERT_NOT_YET_VALID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 160;



  /**
   * The message ID for the message that will be used if a server certificate is
   * rejected because it is expired.  This takes two arguments, which are the
   * subject DN of the certificate and a string representation of the notAfter
   * date.
   */
  public static final int MSGID_EXPCHECK_TRUSTMGR_SERVER_CERT_EXPIRED =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 161;



  /**
   * The message ID for the message that will be used if a server certificate is
   * rejected because it is not yet valid.  This takes two arguments, which are
   * the subject DN of the certificate and a string representation of the
   * notBefore date.
   */
  public static final int MSGID_EXPCHECK_TRUSTMGR_SERVER_CERT_NOT_YET_VALID =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 162;



  /**
   * The message ID for the warning message that will be used if an entry is
   * read from an LDIF file that contains an attribute value that violates the
   * associated syntax.  This takes five arguments, which are the DN of the
   * entry, the starting line number for the entry, the invalid value, the name
   * of the attribute, and a message explaining the reason that the value is not
   * acceptable.
   */
  public static final int MSGID_LDIF_VALUE_VIOLATES_SYNTAX =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_WARNING | 163;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * write a skip file but the specified file already exists.  This takes a
   * single argument, which is the name of the file.
   */
  public static final int MSGID_SKIP_FILE_EXISTS =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_SEVERE_ERROR | 164;


  /**
   * The message ID for the message that will be used if an attempt is made to
   * read an LDIF entry, but that entry does not match the criteria.
   * This takes three arguments, which are the DN of the entry,
   * the starting line number for the entry, and a message that explains why
   * it does not match the criteria.
   */
  public static final int MSGID_LDIF_SKIP =
       CATEGORY_MASK_UTIL | SEVERITY_MASK_MILD_ERROR | 165;


  /**
   * The message ID for the message that will be used as the heading
   * for the table of sub-commands. This does not take any arguments.
   */
  public static final int MSGID_SUBCMDPARSER_SUBCMD_HELP_HEADING =
    CATEGORY_MASK_UTIL | SEVERITY_MASK_INFORMATIONAL | 166;


  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_BASE64_DECODE_INVALID_LENGTH,
                    "The value %s cannot be base64-decoded because it does " +
                    "not have a length that is a multiple of four bytes");
    registerMessage(MSGID_BASE64_DECODE_INVALID_CHARACTER,
                    "The value %s cannot be base64-decoded because it " +
                    "contains an illegal character %s that is not allowed in "+
                    "base64-encoded values");


    registerMessage(MSGID_HEX_DECODE_INVALID_LENGTH,
                    "The value %s cannot be decoded as a hexadecimal string " +
                    "because it does not have a length that is a multiple of " +
                    "two bytes");
    registerMessage(MSGID_HEX_DECODE_INVALID_CHARACTER,
                    "The value %s cannot be decoded as a hexadecimal string " +
                    "because it contains an illegal character %s that is not " +
                    "a valid hexadecimal digit");


    registerMessage(MSGID_EXEC_DISABLED,
                    "The %s command will not be allowed because the " +
                    "Directory Server has been configured to refuse the use " +
                    "of the exec method");


    registerMessage(MSGID_LDIF_INVALID_LEADING_SPACE,
                    "Unable to parse line %d (\"%s\") from the LDIF source " +
                    "because the line started with a space but there were no " +
                    "previous lines in the entry to which this line could be " +
                    "appended");
    registerMessage(MSGID_LDIF_NO_ATTR_NAME,
                    "Unable to parse LDIF entry starting at line %d because " +
                    "the line \"%s\" does not include an attribute name");
    registerMessage(MSGID_LDIF_NO_DN,
                    "Unable to parse LDIF entry starting at line %d because " +
                    "the first line does not contain a DN (the first line " +
                    "was \"%s\"");
    registerMessage(MSGID_LDIF_INVALID_DN_SEPARATOR,
                    "Unable to parse LDIF entry starting at line %d because " +
                    "line \"%s\" contained an invalid separator between the " +
                    "\"dn\" prefix and the actual distinguished name");
    registerMessage(MSGID_LDIF_INVALID_DN,
                    "Unable to parse LDIF entry starting at line %d because " +
                    "an error occurred while trying to parse the value of " +
                    "line \"%s\" as a distinguished name:  %s");
    registerMessage(MSGID_LDIF_INVALID_ATTR_SEPARATOR,
                    "Unable to parse LDIF entry %s starting at line %d " +
                    "because line \"%s\" contained an invalid separator " +
                    "between the attribute name and value");
    registerMessage(MSGID_LDIF_COULD_NOT_BASE64_DECODE_DN,
                    "Unable to parse LDIF entry starting at line %d " +
                    "because it was not possible to base64-decode the DN " +
                    "on line \"%s\":  %s");
    registerMessage(MSGID_LDIF_COULD_NOT_BASE64_DECODE_ATTR,
                    "Unable to parse LDIF entry %s starting at line %d " +
                    "because it was not possible to base64-decode the " +
                    "attribute on line \"%s\":  %s");
    registerMessage(MSGID_LDIF_DUPLICATE_OBJECTCLASS,
                    "Entry %s read from LDIF starting at line %d includes a " +
                    "duplicate objectclass value %s.  The second occurrence " +
                    "of that objectclass has been skipped");
    registerMessage(MSGID_LDIF_VALUE_VIOLATES_SYNTAX,
                    "Entry %s read from LDIF starting at line %d includes " +
                    "value '%s' for attribute %s that is invalid according " +
                    "to the associated syntax:  %s");
    registerMessage(MSGID_LDIF_DUPLICATE_ATTR,
                    "Entry %s read from LDIF starting at line %d includes a " +
                    "duplicate attribute %s with value %s.  The second " +
                    "occurrence of that attribute value has been skipped");
    registerMessage(MSGID_LDIF_MULTIPLE_VALUES_FOR_SINGLE_VALUED_ATTR,
                    "Entry %s starting at line %d includes multiple values " +
                    "for single-valued attribute %s");
    registerMessage(MSGID_LDIF_INVALID_ATTR_SYNTAX,
                    "Unable to parse LDIF entry %s starting at line %d " +
                    "because it has an invalid value \"%s\" for attribute " +
                    "%s:  %s");
    registerMessage(MSGID_LDIF_SCHEMA_VIOLATION,
                    "Entry %s read from LDIF starting at line %d is not " +
                    "valid because it violates the server's schema " +
                    "configuration:  %s");
    registerMessage(MSGID_LDIF_SKIP,
                    "Skipping entry %s because the DN is not one that " +
                    "should be included based on the include and " +
                    "exclude branches");
    registerMessage(MSGID_LDIF_FILE_EXISTS,
                    "The specified LDIF file %s already exists and the " +
                    "export configuration indicates that no attempt should " +
                    "be made to append to or replace the file");
    registerMessage(MSGID_LDIF_INVALID_URL,
                    "Unable to parse LDIF entry %s starting at line %d " +
                    "because the value of attribute %s was to be read from a " +
                    "URL but the URL was invalid:  %s");
    registerMessage(MSGID_LDIF_URL_IO_ERROR,
                    "Unable to parse LDIF entry %s starting at line %d " +
                    "because the value of attribute %s was to be read from " +
                    "URL %s but an error occurred while trying to read that " +
                    "content:  %s");
    registerMessage(MSGID_REJECT_FILE_EXISTS,
                    "The specified reject file %s already exists and the " +
                    "import configuration indicates that no attempt should " +
                    "be made to append to or replace the file");
    registerMessage(MSGID_SKIP_FILE_EXISTS,
                    "The specified skip file %s already exists and the " +
                    "import configuration indicates that no attempt should " +
                    "be made to append to or replace the file");
    registerMessage(MSGID_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_IMPORT,
                    "An error occurred while attempting to determine whether " +
                    "LDIF entry \"%s\" starting at line %d should be " +
                    "imported as a result of the include and exclude filter " +
                    "configuration:  %s");
    registerMessage(MSGID_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_EXPORT,
                    "An error occurred while attempting to determine whether " +
                    "LDIF entry \"%s\" should be exported as a result of the " +
                    "include and exclude filter configuration:  %s");
    registerMessage(MSGID_LDIF_INVALID_DELETE_ATTRIBUTES,
                    "Error in the LDIF change record entry. " +
                    "Invalid attributes specified for the delete operation");
    registerMessage(MSGID_LDIF_NO_MOD_DN_ATTRIBUTES,
                    "Error in the LDIF change record entry. " +
                    "No attributes specified for the mod DN operation");
    registerMessage(MSGID_LDIF_NO_DELETE_OLDRDN_ATTRIBUTE,
                    "Error in the LDIF change record entry. " +
                    "No delete old RDN attribute specified for the mod " +
                    "DN operation");
    registerMessage(MSGID_LDIF_INVALID_DELETE_OLDRDN_ATTRIBUTE,
                    "Error in the LDIF change record entry. " +
                    "Invalid value \"%s\" for the delete old RDN attribute " +
                    "specified for the mod DN operation");
    registerMessage(MSGID_LDIF_INVALID_CHANGERECORD_ATTRIBUTE,
                    "Error in the LDIF change record entry. " +
                    "Invalid attribute \"%s\" specified. " +
                    "Expecting attribute \"%s\"");
    registerMessage(MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE,
                    "Error in the LDIF change record entry. " +
                    "Invalid attribute \"%s\" specified. " +
                    "Expecting one of the following attributes \"%s\"");
    registerMessage(MSGID_LDIF_INVALID_CHANGETYPE_ATTRIBUTE,
                    "Error in the LDIF change record entry. " +
                    "Invalid value \"%s\" for the changetype specified. " +
                    "Expecting one of the following values \"%s\"");
    registerMessage(MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE_VAL,
                    "Error in the LDIF change record entry. " +
                    "Invalid value for the \"%s\" attribute specified. ");


    registerMessage(MSGID_SCHEMANAME_EMPTY_VALUE,
                    "The provided value could not be parsed to determine " +
                    "whether it contained a valid schema element name or OID " +
                    "because it was null or empty");
    registerMessage(MSGID_SCHEMANAME_ILLEGAL_CHAR,
                    "The provided value \"%s\" does not contain a valid " +
                    "schema element name or OID because it contains an " +
                    "illegal character %s at position %d");
    registerMessage(MSGID_SCHEMANAME_CONSECUTIVE_PERIODS,
                    "The provided value \"%s\" does not contain a valid " +
                    "schema element name or OID because the numeric OID " +
                    "contains two consecutive periods at position %d");


    registerMessage(MSGID_ARG_NO_IDENTIFIER,
                    "The %s argument does not have either a single-character " +
                    "or a long identifier that may be used to specify it.  " +
                    "At least one of these must be specified for each " +
                    "argument");
    registerMessage(MSGID_ARG_NO_VALUE_PLACEHOLDER,
                    "The %s argument is configured to take a value but no " +
                    "value placeholder has been defined for it");
    registerMessage(MSGID_ARG_NO_INT_VALUE,
                    "The %s argument does not have any value that may be " +
                    "retrieved as an integer");
    registerMessage(MSGID_ARG_CANNOT_DECODE_AS_INT,
                    "The provided value \"%s\" for the %s argument cannot be " +
                    "decoded as an integer");
    registerMessage(MSGID_ARG_INT_MULTIPLE_VALUES,
                    "The %s argument has multiple values and therefore " +
                    "cannot be decoded as a single integer value");
    registerMessage(MSGID_ARG_NO_BOOLEAN_VALUE,
                    "The %s argument does not have any value that may be " +
                    "retrieved as a Boolean");
    registerMessage(MSGID_ARG_CANNOT_DECODE_AS_BOOLEAN,
                    "The provided value \"%s\" for the %s argument cannot be " +
                    "decoded as a Boolean");
    registerMessage(MSGID_ARG_BOOLEAN_MULTIPLE_VALUES,
                    "The %s argument has multiple values and therefore " +
                    "cannot be decoded as a single Boolean value");


    registerMessage(MSGID_INTARG_LOWER_BOUND_ABOVE_UPPER_BOUND,
                    "The %s argument configuration is invalid because the " +
                    "lower bound of %d is greater than the upper bound of %d");
    registerMessage(MSGID_INTARG_VALUE_BELOW_LOWER_BOUND,
                    "The provided %s value %d is unacceptable because it is " +
                    "below the lower bound of %d");
    registerMessage(MSGID_INTARG_VALUE_ABOVE_UPPER_BOUND,
                    "The provided %s value %d is unacceptable because it is " +
                    "above the upper bound of %d");


    registerMessage(MSGID_BOOLEANARG_NO_VALUE_ALLOWED,
                    "The provided %s value is unacceptable because Boolean " +
                    "arguments are never allowed to have values");


    registerMessage(MSGID_MCARG_VALUE_NOT_ALLOWED,
                    "The provided %s value %s is unacceptable because it is " +
                    "not included in the set of allowed values for that " +
                    "argument");


    registerMessage(MSGID_FILEARG_NO_SUCH_FILE,
                    "The file %s specified for argument %s does not exist");
    registerMessage(MSGID_FILEARG_CANNOT_VERIFY_FILE_EXISTENCE,
                    "An error occurred while trying to verify the existence " +
                    "of file %s specified for argument %s:  %s");
    registerMessage(MSGID_FILEARG_CANNOT_OPEN_FILE,
                    "An error occurred while trying to open file %s " +
                    "specified for argument %s for reading:  %s");
    registerMessage(MSGID_FILEARG_CANNOT_READ_FILE,
                    "An error occurred while trying to read from file %s " +
                    "specified for argument %s:  %s");
    registerMessage(MSGID_FILEARG_EMPTY_FILE,
                    "The file %s specified for argument %s exists but is " +
                    "empty");


    registerMessage(MSGID_ARGPARSER_DUPLICATE_SHORT_ID,
                    "Cannot add argument %s to the argument list because " +
                    "its short identifier -%s conflicts with the %s argument " +
                    "that has already been defined");
    registerMessage(MSGID_ARGPARSER_DUPLICATE_LONG_ID,
                    "Cannot add argument %s to the argument list because " +
                    "its long identifier --%s conflicts with the %s argument " +
                    "that has already been defined");
    registerMessage(MSGID_ARGPARSER_CANNOT_READ_PROPERTIES_FILE,
                    "An error occurred while attempting to read the contents " +
                    "of the argument properties file %s:  %s");
    registerMessage(MSGID_ARGPARSER_TOO_MANY_TRAILING_ARGS,
                    "The provided set of command-line arguments contained " +
                    "too many unnamed trailing arguments.  The maximum " +
                    "number of allowed trailing arguments is %d");
    registerMessage(MSGID_ARGPARSER_LONG_ARG_WITHOUT_NAME,
                    "The provided argument \"%s\" is invalid because it does " +
                    "not include the argument name");
    registerMessage(MSGID_ARGPARSER_NO_ARGUMENT_WITH_LONG_ID,
                    "Argument --%s is not allowed for use with this program");
    registerMessage(MSGID_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID,
                    "Argument --%s requires a value but none was provided");
    registerMessage(MSGID_ARGPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID,
                    "The provided value \"%s\" for argument --%s is not " +
                    "acceptable:  %s");
    registerMessage(MSGID_ARGPARSER_NOT_MULTIVALUED_FOR_LONG_ID,
                    "The argument --%s was included multiple times in the " +
                    "provided set of arguments but it does not allow " +
                    "multiple values");
    registerMessage(MSGID_ARGPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE,
                    "A value was provided for argument --%s but that " +
                    "argument does not take a value");
    registerMessage(MSGID_ARGPARSER_INVALID_DASH_AS_ARGUMENT,
                    "The dash character by itself is invalid for use as an " +
                    "argument name");
    registerMessage(MSGID_ARGPARSER_NO_ARGUMENT_WITH_SHORT_ID,
                    "Argument -%s is not allowed for use with this program");
    registerMessage(MSGID_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID,
                    "Argument -%s requires a value but none was provided");
    registerMessage(MSGID_ARGPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID,
                    "The provided value \"%s\" for argument -%s is not " +
                    "acceptable:  %s");
    registerMessage(MSGID_ARGPARSER_NOT_MULTIVALUED_FOR_SHORT_ID,
                    "The argument -%s was included multiple times in the " +
                    "provided set of arguments but it does not allow " +
                    "multiple values");
    registerMessage(MSGID_ARGPARSER_CANT_MIX_ARGS_WITH_VALUES,
                    "The provided argument block '-%s%s' is illegal because " +
                    "the '%s' argument requires a value but is in the same " +
                    "block as at least one other argument that doesn't " +
                    "require a value");
    registerMessage(MSGID_ARGPARSER_DISALLOWED_TRAILING_ARGUMENT,
                    "Argument \"%s\" does not start with one or two dashes " +
                    "and unnamed trailing arguments are not allowed");
    registerMessage(MSGID_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS,
                    "At least %d unnamed trailing arguments are required " +
                    "in the argument list, but too few were provided");
    registerMessage(MSGID_ARGPARSER_NO_VALUE_FOR_REQUIRED_ARG,
                    "The argument %s is required to have a value but none " +
                    "was provided in the argument list and no default " +
                    "value is available");


    registerMessage(MSGID_MOVEFILE_NO_SUCH_FILE,
                    "The file to move %s does not exist");
    registerMessage(MSGID_MOVEFILE_NOT_FILE,
                    "The file to move %s exists but is not a file");
    registerMessage(MSGID_MOVEFILE_NO_SUCH_DIRECTORY,
                    "The target directory %s does not exist");
    registerMessage(MSGID_MOVEFILE_NOT_DIRECTORY,
                    "The target directory %s exists but is not a directory");


    registerMessage(MSGID_EMAILMSG_INVALID_SENDER_ADDRESS,
                    "The provided sender address %s is invalid:  %s");
    registerMessage(MSGID_EMAILMSG_INVALID_RECIPIENT_ADDRESS,
                    "The provided recipient address %s is invalid:  %s");
    registerMessage(MSGID_EMAILMSG_CANNOT_SEND,
                    "The specified e-mail message could not be sent using " +
                    "any of the configured mail servers");


    registerMessage(MSGID_ARG_SUBCOMMAND_DUPLICATE_SUBCOMMAND,
                    "The argument parser already has a %s subcommand");
    registerMessage(MSGID_ARG_SUBCOMMAND_DUPLICATE_ARGUMENT_NAME,
                    "There are multiple arguments for subcommand %s with " +
                    "name %s");
    registerMessage(MSGID_ARG_SUBCOMMAND_ARGUMENT_GLOBAL_CONFLICT,
                    "Argument %s for subcommand %s conflicts with a global " +
                    "argument with the same name");
    registerMessage(MSGID_ARG_SUBCOMMAND_DUPLICATE_SHORT_ID,
                    "Argument %s for subcommand %s has a short identifier " +
                    "-%s that conflicts with that of argument %s");
    registerMessage(MSGID_ARG_SUBCOMMAND_ARGUMENT_SHORT_ID_GLOBAL_CONFLICT,
                    "Argument %s for subcommand %s has a short ID -%s that " +
                    "conflicts with that of global argument %s");
    registerMessage(MSGID_ARG_SUBCOMMAND_DUPLICATE_LONG_ID,
                    "Argument %s for subcommand %s has a long identifier " +
                    "--%s that conflicts with that of argument %s");
    registerMessage(MSGID_ARG_SUBCOMMAND_ARGUMENT_LONG_ID_GLOBAL_CONFLICT,
                    "Argument %s for subcommand %s has a long ID --%s that " +
                    "conflicts with that of global argument %s");


    registerMessage(MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_NAME,
                    "There is already another global argument named \"%s\"");
    registerMessage(MSGID_SUBCMDPARSER_GLOBAL_ARG_NAME_SUBCMD_CONFLICT,
                    "The argument name %s conflicts with the name of another " +
                    "argument associated with the %s subcommand");
    registerMessage(MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_SHORT_ID,
                    "Short ID -%s for global argument %s conflicts with the " +
                    "short ID of another global argument %s");
    registerMessage(MSGID_SUBCMDPARSER_GLOBAL_ARG_SHORT_ID_CONFLICT,
                    "Short ID -%s for global argument %s conflicts with the " +
                    "short ID for the %s argument associated with subcommand " +
                    "%s");
    registerMessage(MSGID_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_LONG_ID,
                    "Long ID --%s for global argument %s conflicts with the " +
                    "long ID of another global argument %s");
    registerMessage(MSGID_SUBCMDPARSER_GLOBAL_ARG_LONG_ID_CONFLICT,
                    "Long ID --%s for global argument %s conflicts with the " +
                    "long ID for the %s argument associated with subcommand " +
                    "%s");
    registerMessage(MSGID_SUBCMDPARSER_CANNOT_READ_PROPERTIES_FILE,
                    "An error occurred while attempting to read the contents " +
                    "of the argument properties file %s:  %s");
    registerMessage(MSGID_SUBCMDPARSER_LONG_ARG_WITHOUT_NAME,
                    "The provided command-line argument %s does not contain " +
                    "an argument name");
    registerMessage(MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID,
                    "The provided argument --%s is not a valid global " +
                    "argument identifier");
    registerMessage(MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_LONG_ID,
                    "The provided argument --%s is not a valid global or " +
                    "subcommand argument identifier");
    registerMessage(MSGID_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID,
                    "Command-line argument --%s requires a value but none " +
                    "was given");
    registerMessage(MSGID_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID,
                    "The provided value \"%s\" for argument --%s is not " +
                    "acceptable:  %s");
    registerMessage(MSGID_SUBCMDPARSER_NOT_MULTIVALUED_FOR_LONG_ID,
                    "The argument --%s was included multiple times in the " +
                    "provided set of arguments but it does not allow " +
                    "multiple values");
    registerMessage(MSGID_SUBCMDPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE,
                    "A value was provided for argument --%s but that " +
                    "argument does not take a value");
    registerMessage(MSGID_SUBCMDPARSER_INVALID_DASH_AS_ARGUMENT,
                    "The dash character by itself is invalid for use as an " +
                    "argument name");
    registerMessage(MSGID_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID,
                    "The provided argument -%s is not a valid global " +
                    "argument identifier");
    registerMessage(MSGID_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID,
                    "The provided argument -%s is not a valid global or " +
                    "subcommand argument identifier");
    registerMessage(MSGID_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID,
                    "Argument -%s requires a value but none was provided");
    registerMessage(MSGID_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID,
                    "The provided value \"%s\" for argument -%s is not " +
                    "acceptable:  %s");
    registerMessage(MSGID_SUBCMDPARSER_NOT_MULTIVALUED_FOR_SHORT_ID,
                    "The argument -%s was included multiple times in the " +
                    "provided set of arguments but it does not allow " +
                    "multiple values");
    registerMessage(MSGID_SUBCMDPARSER_CANT_MIX_ARGS_WITH_VALUES,
                    "The provided argument block '-%s%s' is illegal because " +
                    "the '%s' argument requires a value but is in the same " +
                    "block as at least one other argument that doesn't " +
                    "require a value");
    registerMessage(MSGID_SUBCMDPARSER_INVALID_ARGUMENT,
                    "The provided argument %s is not recognized");
    registerMessage(MSGID_SUBCMDPARSER_MULTIPLE_SUBCOMMANDS,
                    "The provided argument %s specifies a valid subcommand, " +
                    "but another subcommand %s was also given.  Only a " +
                    "single subcommand may be provided");
    registerMessage(MSGID_SUBCMDPARSER_NO_VALUE_FOR_REQUIRED_ARG,
                    "The argument %s is required to have a value but none " +
                    "was provided in the argument list and no default " +
                    "value is available");


    registerMessage(MSGID_LDAPURL_NO_COLON_SLASH_SLASH,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because it does not contain the necessary :// " +
                    "component to separate the scheme from the rest of the " +
                    "URL");
    registerMessage(MSGID_LDAPURL_NO_SCHEME,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because it does not contain a protocol scheme");
    registerMessage(MSGID_LDAPURL_NO_HOST,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because it does not contain a host before the colon " +
                    "to specify the port number");
    registerMessage(MSGID_LDAPURL_NO_PORT,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because it does not contain a port number after the " +
                    "colon following the host");
    registerMessage(MSGID_LDAPURL_CANNOT_DECODE_PORT,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because the port number portion %s cannot be " +
                    "decoded as an integer");
    registerMessage(MSGID_LDAPURL_INVALID_PORT,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because the provided port number %d is not within " +
                    "the valid range between 1 and 65535");
    registerMessage(MSGID_LDAPURL_INVALID_SCOPE_STRING,
                    "The provided string \"%s\" cannot be decoded as an LDAP " +
                    "URL because the scope string %s was not one of the " +
                    "allowed values of base, one, sub, or subordinate");
    registerMessage(MSGID_LDAPURL_PERCENT_TOO_CLOSE_TO_END,
                    "The provided URL component \"%s\" could not be decoded " +
                    "because the percent character at byte %d was not " +
                    "followed by two hexadecimal digits");
    registerMessage(MSGID_LDAPURL_INVALID_HEX_BYTE,
                    "The provided URL component \"%s\" could not be " +
                    "decoded because the character at byte %d was not a " +
                    "valid hexadecimal digit");
    registerMessage(MSGID_LDAPURL_CANNOT_CREATE_UTF8_STRING,
                    "An error occurred while attempting to represent a byte " +
                    "array as a UTF-8 string during the course of decoding a " +
                    "portion of an LDAP URL:  %s");


    registerMessage(MSGID_CHARSET_CONSTRUCTOR_NO_NAME,
                    "The named character set is invalid because it does not " +
                    "contain a name");
    registerMessage(MSGID_CHARSET_CONSTRUCTOR_INVALID_NAME_CHAR,
                    "The named character set is invalid because the provide " +
                    "name \"%s\" has an invalid character at position %d.  " +
                    "Only ASCII alphabetic characters are allowed in the " +
                    "name");
    registerMessage(MSGID_CHARSET_NO_COLON,
                    "Cannot decode value \"%s\" as a named character set " +
                    "because it does not contain a colon to separate the " +
                    "name from the set of characters");
    registerMessage(MSGID_CHARSET_NO_NAME,
                    "Cannot decode value \"%s\" as a named character set " +
                    "because it does not contain a name to use for the " +
                    "character set");
    registerMessage(MSGID_CHARSET_NO_CHARS,
                    "Cannot decode value \"%s\" as a named character set " +
                    "because there are no characters to include in the set");


    registerMessage(MSGID_TIME_IN_SECONDS,
                    "%d seconds");
    registerMessage(MSGID_TIME_IN_MINUTES_SECONDS,
                    "%d minutes, %s seconds");
    registerMessage(MSGID_TIME_IN_HOURS_MINUTES_SECONDS,
                    "%d hours, %d minutes, %s seconds");
    registerMessage(MSGID_TIME_IN_DAYS_HOURS_MINUTES_SECONDS,
                    "%d days, %d hours, %d minutes, %s seconds");


    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_TEMPORARILY_LOCKED,
                    "account-temporarily-locked");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_PERMANENTLY_LOCKED,
                    "account-permanently-locked");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_UNLOCKED,
                    "account-unlocked");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_IDLE_LOCKED,
                    "account-idle-locked");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_RESET_LOCKED,
                    "account-reset-locked");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_DISABLED,
                    "account-disabled");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_ENABLED,
                    "account-enabled");
    registerMessage(MSGID_ACCTNOTTYPE_ACCOUNT_EXPIRED,
                    "account-expired");
    registerMessage(MSGID_ACCTNOTTYPE_PASSWORD_EXPIRED,
                    "password-expired");
    registerMessage(MSGID_ACCTNOTTYPE_PASSWORD_EXPIRING,
                    "password-expiring");
    registerMessage(MSGID_ACCTNOTTYPE_PASSWORD_RESET,
                    "password-reset");
    registerMessage(MSGID_ACCTNOTTYPE_PASSWORD_CHANGED,
                    "password-changed");


    registerMessage(MSGID_FILEPERM_SET_NO_SUCH_FILE,
                    "Unable to set permissions for file %s because it does " +
                    "not exist");
    registerMessage(MSGID_FILEPERM_CANNOT_EXEC_CHMOD,
                    "Unable to execute the chmod command to set file " +
                    "permissions on %s:  %s");
    registerMessage(MSGID_FILEPERM_SET_JAVA_EXCEPTION,
                    "One or more exceptions were thrown in the process of " +
                    "updating the file permissions for %s.  Some of the " +
                    "permissions for the file may have been altered");
    registerMessage(MSGID_FILEPERM_SET_JAVA_FAILED_ALTERED,
                    "One or more updates to the file permissions for %s " +
                    "failed, but at least one update was successful.  Some " +
                    "of the permissions for the file may have been altered");
    registerMessage(MSGID_FILEPERM_SET_JAVA_FAILED_UNALTERED,
                    "All of the attempts to update the file permissions for " +
                    "%s failed.  The file should be left with its original " +
                    "permissions");
    registerMessage(MSGID_FILEPERM_INVALID_UNIX_MODE_STRING,
                    "The provided string %s does not represent a valid UNIX " +
                    "file mode.  UNIX file modes must be a three-character " +
                    "string in which each character is a numeric digit " +
                    "between zero and seven");

    registerMessage(MSGID_VALIDATOR_PRECONDITION_NOT_MET,
                    "A precondition of the invoked method was not met.  This " +
                    "This usually means there is a defect somewhere in the " +
                    "call stack.  Details: %s");

    registerMessage(MSGID_GLOBAL_OPTIONS,
                    "Global Options:");
    registerMessage(MSGID_GLOBAL_OPTIONS_REFERENCE,
                    "See \"%s --help\"");
    registerMessage(MSGID_SUBCMD_OPTIONS,
                    "SubCommand Options:");
    registerMessage(MSGID_ARGPARSER_USAGE,
                    "Usage:");
    registerMessage(MSGID_SUBCMDPARSER_SUBCMD_HEADING,
                    "Available subcommands:");
    registerMessage(MSGID_SUBCMDPARSER_SUBCMD_HELP_HEADING,
                    "To get the list of subcommands use:");
    registerMessage(MSGID_SUBCMDPARSER_SUBCMD_REFERENCE,
                    "See \"%s --help-{category}\"");
    registerMessage(MSGID_SUBCMDPARSER_GLOBAL_HEADING,
                    "The accepted value for global options are:");
    registerMessage(MSGID_GLOBAL_HELP_REFERENCE,
                    "See \"%s --help\" to get more usage help");

    registerMessage(MSGID_RENAMEFILE_CANNOT_DELETE_TARGET,
                    "Failed to delete target file %s.  Make sure the file is " +
                    "not currently in use by this or another application");

    registerMessage(MSGID_RENAMEFILE_CANNOT_RENAME,
                    "Failed to rename file %s to %s");


    registerMessage(MSGID_EXPCHECK_TRUSTMGR_CLIENT_CERT_EXPIRED,
                    "Refusing to trust client or issuer certificate '%s' " +
                    "because it expired on %s");
    registerMessage(MSGID_EXPCHECK_TRUSTMGR_CLIENT_CERT_NOT_YET_VALID,
                    "Refusing to trust client or issuer certificate '%s' " +
                    "because it is not valid until %s");
    registerMessage(MSGID_EXPCHECK_TRUSTMGR_SERVER_CERT_EXPIRED,
                    "Refusing to trust server or issuer certificate '%s' " +
                    "because it expired on %s");
    registerMessage(MSGID_EXPCHECK_TRUSTMGR_SERVER_CERT_NOT_YET_VALID,
                    "Refusing to trust server or issuer certificate '%s' " +
                    "because it is not valid until %s");
  }
}

