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



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the server configuration.
 */
public class ConfigMessages
{
  /**
   * The message ID for the message that will be used if an attempt is made to
   * update a required attribute so that it would have no values.  This takes a
   * single string argument, which is the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_IS_REQUIRED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 1;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign or add a set of values to a configuration attribute in which one of
   * the values was not acceptable.  This takes three arguments, which are the
   * rejected value for the configuration attribute, the name of the attribute,
   * and the reason that the value was rejected.
   */
  public static final int MSGID_CONFIG_ATTR_REJECTED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 2;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign multiple values to a single-valued attribute.  This takes a single
   * argument, which is the name of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 3;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add one or more values to a single-valued attribute such that it would
   * contain multiple values.  This takes a single argument, which is the name
   * of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_ADD_VALUES_IS_SINGLE_VALUED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 4;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add one or more values to a configuration attribute in which one of the
   * values already exists.  This takes a single argument, which is the name of
   * the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_ADD_VALUES_ALREADY_EXISTS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 5;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a value a configuration attribute that does not contain that value.
   * This takes two arguments, which are the value to remove and the name of the
   * attribute.
   */
  public static final int MSGID_CONFIG_ATTR_NO_SUCH_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 6;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign an inappropriate value to a boolean attribute.  This takes a single
   * argument, which is the provided invalid value.
   */
  public static final int MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 7;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve the int or long value of an integer attribute that does not have
   * any values.  This takes a single argument, which is the name of the
   * attribute.
   */
  public static final int MSGID_CONFIG_ATTR_NO_INT_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 8;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve the int or long value of an integer attribute that has multiple
   * values.  This takes a single argument, which is the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_MULTIPLE_INT_VALUES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 9;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve the value of an integer attribute as a Java <CODE>int</CODE> but
   * the value was not within the acceptable range for an <CODE>int</CODE>.
   * This takes a single argument, which is the name of the configuration
   * attribute.
   */
  public static final int MSGID_CONFIG_ATTR_VALUE_OUT_OF_INT_RANGE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 10;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign an inappropriate value to an integer attribute.  This takes two
   * arguments, which are the invalid value that was provided and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ATTR_INVALID_INT_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 11;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign a value to an integer configuration attribute that is below the
   * lower bound configured for that attribute.  This takes three arguments,
   * which are the name of the attribute, the provided value, and the lower
   * bound.
   */
  public static final int MSGID_CONFIG_ATTR_INT_BELOW_LOWER_BOUND =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 12;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * assign a value to an integer configuration attribute that is above the
   * upper bound configured for that attribute.  This takes three arguments,
   * which are the name of the attribute, the provided value, and the upper
   * bound.
   */
  public static final int MSGID_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 13;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * parse a value string for an integer configuration attribute that cannot be
   * decoded as a long.  This takes three arguments, which are the value that
   * could not be parsed, the name of the configuration attribute, and the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_ATTR_INT_COULD_NOT_PARSE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 14;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve the value of a string attribute that does not have any values.
   * This takes a single argument, which is the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_NO_STRING_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 15;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve the value of a string attribute that has multiple values.  This
   * takes a single argument, which is the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_MULTIPLE_STRING_VALUES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 16;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * include a null or empty value in the set of values for a string
   * configuration attribute.  This takes a single argument, which is the name
   * of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_EMPTY_STRING_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 17;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * set the value of a multi-choice parameter to include a value that is not
   * allowed.  This takes two arguments, which are the value and the name of the
   * configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_VALUE_NOT_ALLOWED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 18;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * set the value of an integer with unit parameter to include a unit that is
   * not allowed.  This takes two arguments, which are the name of the unit and
   * the name of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_INVALID_UNIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 19;



  /**
   * The message ID for the message that will be used if the delimiter between
   * an integer value and an associated unit could not be found.  This takes two
   * arguments, which are the value and name of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_NO_UNIT_DELIMITER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 20;



  /**
   * The message ID for the message that will be used if it was not possible to
   * decode the integer component of an integer with unit attribute.  This takes
   * three arguments, which are the value and name of the configuration
   * attribute and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ATTR_COULD_NOT_PARSE_INT_COMPONENT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 21;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as an integer with unit parameter.  This takes three arguments,
   * which are the value, the name of the configuration attribute, and the
   * reason that the value is not acceptable.
   */
  public static final int MSGID_CONFIG_ATTR_INVALID_VALUE_WITH_UNIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 22;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a child to a configuration entry that already has a child with the same
   * DN.  This takes two arguments, which are the DN of the child entry and the
   * DN of the entry with that conflicting child.
   */
  public static final int MSGID_CONFIG_ENTRY_CONFLICTING_CHILD =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 23;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a child entry below a configuration entry that does not have a child
   * with the specified DN.  This takes two arguments, which are the DN of the
   * child entry that was to be removed and the DN of the entry that should have
   * contained that child.
   */
  public static final int MSGID_CONFIG_ENTRY_NO_SUCH_CHILD =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 24;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a child entry below a configuration entry but that child was not a
   * leaf entry.  This takes two arguments, which are the DN of the child entry
   * that was to be removed and the DN of the entry that contained that child.
   */
  public static final int MSGID_CONFIG_ENTRY_CANNOT_REMOVE_NONLEAF =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 25;



  /**
   * The message ID for the message that will be used if the specified
   * configuration file does not exist.  This takes a single argument, which is
   * the path to the configuration file that the server attempted to access.
   */
  public static final int MSGID_CONFIG_FILE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 26;



  /**
   * The message ID for the message that will be used an error occurs while
   * attempting to verify whether the configuration file exists.  This takes two
   * arguments, which are the path to the configuration file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_CANNOT_VERIFY_EXISTENCE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 27;



  /**
   * The message ID for the message that will be used if the configuration
   * handler cannot open the configuration file for reading.  This takes two
   * arguments, which are the path to the configuration file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_CANNOT_OPEN_FOR_READ =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 28;



  /**
   * The message ID for the message that will be used if an I/O error occurs
   * while attempting to read from the configuration file.  This takes two
   * arguments, which are the path to the configuration file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_READ_ERROR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 29;



  /**
   * The message ID for the message that will be used if an attribute with one
   * or more invalid attribute options is encountered while interacting with the
   * Directory Server configuration.  This takes two arguments, which are the DN
   * of the entry and the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_OPTIONS_NOT_ALLOWED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 30;



  /**
   * The message ID for the message that will be used if an entry in the
   * configuration file cannot be parsed as a valid LDIF entry.  This takes
   * three arguments, which are the approximate line number in the LDIF file,
   * the path to the LDIF file, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_FILE_INVALID_LDIF_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 31;



  /**
   * The message ID for the message that will be used if the configuration LDIF
   * file appears to be empty (or at least not contain any entries).  This takes
   * a single argument, which is the path to the configuration file.
   */
  public static final int MSGID_CONFIG_FILE_EMPTY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 32;



  /**
   * The message ID for the message that will be used if the first entry read
   * from the LDIF configuration file did not have the expected DN.  This takes
   * three arguments, which are the path to the LDIF file, the DN of the first
   * entry read from the file, and the expected configuration root DN.
   */
  public static final int MSGID_CONFIG_FILE_INVALID_BASE_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 33;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while attempting to process the LDIF configuration file.  This takes
   * two arguments, which are the path to the configuration file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_GENERIC_ERROR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 34;



  /**
   * The message ID for the message that will be used if a duplicate entry is
   * detected in the configuration.  This takes three arguments, which are the
   * DN of the duplicate entry, the starting line number for the duplicate
   * entry, and the path to the LDIF configuration file.
   */
  public static final int MSGID_CONFIG_FILE_DUPLICATE_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 35;



  /**
   * The message ID for the message that will be used if an entry is read from
   * the configuration that does not have a parent.  This takes four arguments,
   * which are the DN of the entry with no parent, the starting line number for
   * that entry, the path to the LDIF configuration file, and the expected
   * parent DN.
   */
  public static final int MSGID_CONFIG_FILE_NO_PARENT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 36;



  /**
   * The message ID for the message that will be used if it is not possible to
   * determine the appropriate parent DN for a given configuration entry.  This
   * takes three arguments, which are the DN of the entry, the starting line
   * number for that entry, and the path to the LDIF configuration file.
   */
  public static final int MSGID_CONFIG_FILE_UNKNOWN_PARENT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 37;



  /**
   * The message ID for the message that will be used if it is not possible to
   * determine the server root directory for this instance of the Directory
   * Server.  This takes a single argument, which is the name of the environment
   * variable that may be set to specify the server root.
   */
  public static final int MSGID_CONFIG_CANNOT_DETERMINE_SERVER_ROOT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 38;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to write an entry to an LDIF source.  This takes two
   * arguments, which are the DN of the entry and string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_ERROR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 39;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to close an LDIF writer.  This takes a single argument,
   * which is the string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_CLOSE_ERROR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 40;



  /**
   * The message ID for the message that will be used if an attempt is made
   * to import the Directory Server configuration from an existing LDIF other
   * than reading it at startup.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_FILE_UNWILLING_TO_IMPORT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 41;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the logger base entry from the configuration.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 42;



  /**
   * The message ID for the message that will be used if the logger
   * base entry does not exist in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_LOGGER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 43;



  /**
   * The message ID for the message that will be used if there are no active
   * access loggers defined in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 44;



  /**
   * The message ID for the message that will be used if there are no active
   * error loggers defined in the Directory Server configuration.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 45;



  /**
   * The message ID for the message that will be used if there are no active
   * debug loggers defined in the Directory Server configuration.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_ACTIVE_DEBUG_LOGGERS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_WARNING | 46;



  /**
   * The message ID for the message that will be used if a logger configuration
   * entry does not contain an acceptable logger configuration.  This takes two
   * arguments, which are the DN of the configuration entry and the reason that
   * it is not acceptable.
   */
  public static final int MSGID_CONFIG_LOGGER_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 47;



  /**
   * The message ID for the message that will be used as an unacceptable reason
   * whenever an actual reason cannot be determined.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_UNKNOWN_UNACCEPTABLE_REASON =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 48;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a logger from a configuration entry.  This takes two
   * arguments, which are the DN of the configuration entry and a message that
   * explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_LOGGER_CANNOT_CREATE_LOGGER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 49;



  /**
   * The message ID for the message that will be used if an entry below the
   * logger base does not contain a valid logger objectclass.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 50;



  /**
   * The message ID for the description of the logger class name configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 51;



  /**
   * The message ID for the message that will be used if an entry below the
   * logger base does not contain a value for the logger class name.  This takes
   * a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 52;



  /**
   * The message ID for the message that will be used if an entry below the
   * logger base contains an invalid value for the class name.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 53;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server access logger but the associated class cannot be
   * instantiated as an access logger.  This takes three arguments, which are
   * the logger class name, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 54;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server error logger but the associated class cannot be
   * instantiated as an error logger.  This takes three arguments, which are the
   * logger class name, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 55;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server debug logger but the associated class cannot be
   * instantiated as a debug logger.  This takes three arguments, which are the
   * logger class name, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 56;



  /**
   * The message ID for the description of the logger enabled configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_LOGGER_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 57;



  /**
   * The message ID for the message that will be used if an entry below the
   * logger base does not contain a value for the enabled attribute.  This takes
   * a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 58;



  /**
   * The message ID for the message that will be used if an entry below the
   * logger base has an invalid value for the enabled attribute.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 59;



  /**
   * The message ID for the description of the allow attribute name exceptions
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_ALLOW_ATTR_EXCEPTIONS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 60;



  /**
   * The message ID for the message that will be used if the allow attribute
   * name exceptions configuration attribute has an invalid value.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_ALLOW_EXCEPTIONS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 61;



  /**
   * The message ID for the description of the configuration attribute used to
   * specify the behavior for attributes that do not conform to the associated
   * syntax.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_INVALID_SYNTAX_BEHAVIOR =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 62;



  /**
   * The message ID for the message that will be used if the invalid syntax
   * behavior attribute has an invalid value.  This takes two arguments, which
   * are the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if a configuration
   * attribute contains more than one pending value set.  This takes a single
   * argument, which is the name of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_MULTIPLE_PENDING_VALUE_SETS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 64;



  /**
   * The message ID for the message that will be used if a configuration
   * attribute contains more than one active value set.  This takes a single
   * argument, which is the name of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_MULTIPLE_ACTIVE_VALUE_SETS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 65;



  /**
   * The message ID for the message that will be used if a configuration
   * attribute does not have an active value set.  This takes a single argument,
   * which is the name of the configuration attribute.
   */
  public static final int MSGID_CONFIG_ATTR_NO_ACTIVE_VALUE_SET =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 66;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as an integer value but the object was of an invalid
   * type.  This takes three arguments, which are a string representation of the
   * provided value, the name of the configuration attribute and the object
   * type for the provided value.
   */
  public static final int MSGID_CONFIG_ATTR_INT_INVALID_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 67;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as an array of integer values but the object was an array
   * with an invalid component type.  This takes two arguments, which are the
   * name of the configuration attribute and the component type for the
   * provided array.
   */
  public static final int MSGID_CONFIG_ATTR_INT_INVALID_ARRAY_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 68;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as a string value but an error occurred in the process.
   * This takes three arguments, which are a string representation of the
   * provided value, the name of the configuration attribute and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ATTR_INVALID_STRING_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 69;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as a string value but the object was of an invalid type.
   * This takes three arguments, which are a string representation of the
   * provided value, the name of the configuration attribute and the object
   * type for the provided value.
   */
  public static final int MSGID_CONFIG_ATTR_STRING_INVALID_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 70;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as an array of string values but the object was an array
   * with an invalid component type.  This takes two arguments, which are the
   * name of the configuration attribute and the component type for the
   * provided array.
   */
  public static final int MSGID_CONFIG_ATTR_STRING_INVALID_ARRAY_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 71;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as an integer with unit value but the object was of an
   * invalid type.  This takes three arguments, which are a string
   * representation of the provided value, the name of the configuration
   * attribute and the object type for the provided value.
   */
  public static final int MSGID_CONFIG_ATTR_INT_WITH_UNIT_INVALID_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 72;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve a JMX attribute from a configuration entry but that attribute does
   * not have an active value.  This takes two arguments, which are the DN of
   * the configuration entry and the name of the attribute.
   */
  public static final int MSGID_CONFIG_JMX_ATTR_NO_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 73;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * retrieve a JMX attribute from a configuration entry that does not manage
   * that attribute through JMX.  This takes two arguments, which are the DN of
   * the configuration entry and the name of the attribute.
   */
  public static final int MSGID_CONFIG_JMX_ATTR_NO_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 74;



  /**
   * The message ID for the message that will be used if an attempt to retrieve
   * the configuration entry associated with a JMX MBean fails for some reason.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_JMX_CANNOT_GET_CONFIG_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 75;



  /**
   * The message ID for the message that will be used if an attempt to update a
   * configuration attribute over JMX would have resulted in an invalid value
   * for that attribute.  This takes three arguments, which are the name of the
   * attribute with the invalid value, the DN of the associated configuration
   * entry, and the message or exception associated with the failure.
   */
  public static final int MSGID_CONFIG_JMX_ATTR_INVALID_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 76;



  /**
   * The message ID for the message that will be used if an attempt to update
   * a configuration entry over JMX would have resulted in that entry having an
   * unacceptable configuration.  This takes two arguments, which are the DN of
   * the configuration entry and the message explaining the problem(s) with the
   * update(s).
   */
  public static final int MSGID_CONFIG_JMX_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 77;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * invoke a JMX method for a component that does not have any method matching
   * the given signature.  This takes two arguments, which are the signature of
   * the method that should have been invoked and the DN of the configuration
   * entry with which the invokable component is associated.
   */
  public static final int MSGID_CONFIG_JMX_NO_METHOD =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 78;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to retrieve a specified configuration entry.  This takes two
   * arguments, which are the DN of the requested configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CANNOT_GET_CONFIG_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 79;



  /**
   * The message ID for the message that will be used to indicate that a
   * configuration attribute has been updated.  This takes three arguments,
   * which are the name of the attribute, a string representation of the value,
   * and the DN of the configuration entry in which the attribute resides.
   */
  public static final int MSGID_CONFIG_SET_ATTRIBUTE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 80;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to apply a change to a configurable component such that not all
   * of the changes were applied successfully.  This takes two arguments, which
   * are the DN of the associated configuration entry and a message detailing
   * the problem(s) encountered.
   */
  public static final int MSGID_CONFIG_CHANGE_NOT_SUCCESSFUL =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 81;



  /**
   * The message ID for the message that will be used if an entry below the
   * logger base does not contain a value for the logger file name. This takes
   * a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_FILE_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 82;



  /**
   * The message ID for the string that will be used if the Directory Server
   * cannot register an MBean with the MBean server.  This takes two arguments,
   * which are the DN of the configuration entry for the component associated
   * with the MBean and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_JMX_CANNOT_REGISTER_MBEAN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 83;



  /**
   * The message ID for the string that will be used if a problem occurs while
   * attempting to export the configuration to LDIF.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_LDIF_WRITE_ERROR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 84;



  /**
   * The message ID for the description of the configuration attribute that
   * specifies the number of worker threads.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_NUM_THREADS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 85;



  /**
   * The message ID for the description of the configuration attribute that
   * specifies the maximum work queue capacity.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_MAX_CAPACITY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 86;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the number of worker threads to use.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_NUM_WORKER_THREADS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 87;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the maximum work queue capacity.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_QUEUE_CAPACITY =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 88;



  /**
   * The message ID for the message that will be used if an invalid value is
   * specified for the number of worker threads to use to service the work
   * queue.  This takes two arguments, which are the DN of the configuration
   * entry and the specified number of worker threads.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_NUM_THREADS_INVALID_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 89;



  /**
   * The message ID for the message that will be used if an invalid value is
   * specified for the maximum work queue capacity.  This takes two arguments,
   * which are the DN of the configuration entry and the specified capacity.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_CAPACITY_INVALID_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 90;



  /**
   * The message ID for the message that will be used if the number of worker
   * threads is increased while the server is online.  This takes two arguments,
   * which are the number of new threads that have been created and the total
   * number of worker threads after the change.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_CREATED_THREADS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 91;



  /**
   * The message ID for the message that will be used if the number of worker
   * threads is decreased while the server is online.  This takes two arguments,
   * which are the number of new threads that will be destroyed and the total
   * number of worker threads after the change.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_DESTROYING_THREADS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 92;



  /**
   * The message ID for the message that will be used if the work queue capacity
   * is changed while the server is online.  This takes a single argument, which
   * is the new capacity for the work queue.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_NEW_CAPACITY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 93;



  /**
   * The message ID for the message that will be used if a worker thread
   * experiences too many unexpected failures while waiting for operations to
   * process.  This takes three arguments, which are the name of the worker
   * thread, the number of failures experienced, and the maximum number of
   * failures allowed.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 94;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to create a monitor provider to publish information about the state
   * of the work queue.  This takes two arguments, which are the fully-qualified
   * class name of the monitor provider and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 95;



  /** The message ID used to describe the backend directory configuration
   * attribute.
   */
  public static final int MSGID_CONFIG_DESCRIPTION_BACKEND_DIRECTORY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 96;



  /**
   * The message ID used when there is no valid backend directory provided.
   */
  public static final int MSGID_CONFIG_BACKEND_NO_DIRECTORY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 97;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * include a null value in the set of values for a DN configuration attribute.
   * This takes a single argument, which is the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_DN_NULL =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 98;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the value of a configuration attribute as a DN.  This takes
   * three arguments, which are the provided value, the name of the attribute,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ATTR_DN_CANNOT_PARSE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 99;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as a DN value but an error occurred in the process.  This
   * takes three arguments, which are a string representation of the provided
   * value, the name of the configuration attribute and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ATTR_INVALID_DN_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 100;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as a DN value but the object was of an invalid type.  This
   * takes three arguments, which are a string representation of the provided
   * value, the name of the configuration attribute and the object type for the
   * provided value.
   */
  public static final int MSGID_CONFIG_ATTR_DN_INVALID_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 101;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * decode an object as an array of DN values but the object was an array with
   * an invalid component type.  This takes two arguments, which are the name of
   * the configuration attribute and the component type for the provided array.
   */
  public static final int MSGID_CONFIG_ATTR_DN_INVALID_ARRAY_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 102;



  /**
   * The message ID for the message that will be used if the config handler
   * cannot register its base DN as a private suffix.  This takes two arguments,
   * which are the suffix DN that was attempted to be registered, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CANNOT_REGISTER_AS_PRIVATE_SUFFIX =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 103;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the base entry for the server backends.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 104;



  /**
   * The message ID for the message that will be used if the configuration base
   * entry for the set of backends does not exist in the server.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_BACKEND_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 105;



  /**
   * The message ID for the message that will be used if a configuration entry
   * below the backend config base does not appear to contain a backend
   * configuration.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int
       MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 106;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to interact with a backend configuration entry.  This
   * takes two arguments, which are the DN of the backend configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 107;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that controls whether a backend is enabled.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 108;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the class name for a backend
   * implementation.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 109;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not contain an attribute to indicate whether the backend is
   * enabled or disabled.  This takes a single argument, which is the DN of the
   * backend configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 110;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry is marked disabled and therefore will not be processed.  This takes a
   * single argument, which is the DN of the backend configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 111;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine whether a backend should be enabled.
   * This takes two arguments, which are the DN of the backend configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 112;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not contain the name of the class providing the backend
   * implementation.  This takes a single argument, which is the DN of the
   * backend configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 113;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the name of the class to use for a backend
   * implementation.  This takes two arguments, which are the DN of the backend
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 114;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load and instantiate a backend class.  This takes three
   * arguments, which are the name of the backend class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 115;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize a backend from a configuration entry.  This takes
   * three arguments, which are the name of the backend class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 116;



  /**
   * The message ID for the message that will be used if class defined for a
   * backend does not contain an actual backend implementation.  This takes two
   * arguments, which are the name of the class and the DN of the configuration
   * entry.
   */
  public static final int MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 117;



  /**
   * The message ID for the message that will be used if the class for a backend
   * is changed while that backend is online to indicate that the change will
   * not take effect immediately.  This takes three arguments, which are the DN
   * of the configuration entry, the name of the class that is currently
   * providing the backend implementation, and the name of the new class to use
   * for the backend implementation.
   */
  public static final int MSGID_CONFIG_BACKEND_ACTION_REQUIRED_TO_CHANGE_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_NOTICE | 118;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a backend that contains one or more subordinate backends.  This
   * takes a single argument, which is the DN of the configuration entry with
   * which the backend is associated.
   */
  public static final int
       MSGID_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_NOTICE | 119;



  /**
   * The message ID for the description of the max allowed client connections
   * configuration attribute.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_MAX_ALLOWED_CONNECTIONS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 120;



  /**
   * The message ID for the message that will be used if the max allowed client
   * connections configuration attribute has an invalid value.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_MAX_ALLOWED_CONNECTIONS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 121;



  /**
   * The message ID for the message that will be used if the logger class has
   * changed and will require administrative action to take effect.  This takes
   * three arguments, which are the old class name, the new class name, and the
   * DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 122;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new logger with a DN that matches the DN of a logger that already
   * exists.  This takes a single argument, which is the DN of the logger
   * configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 123;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server access logger.  This takes three arguments,
   * which are the class name for the logger class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_ACCESS_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 124;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server error logger.  This takes three arguments,
   * which are the class name for the logger class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_ERROR_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 125;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server debug logger.  This takes three arguments,
   * which are the class name for the logger class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_LOGGER_DEBUG_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 126;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the monitor base entry from the configuration.
   * This takes a single argument, which is a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_MONITOR_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 127;



  /**
   * The message ID for the message that will be used if the monitor base entry
   * does not exist in the Directory Server configuration.  This does not take
   * any arguments.
   */
  public static final int MSGID_CONFIG_MONITOR_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 128;



  /**
   * The message ID for the message that will be used if a monitor configuration
   * entry does not contain an acceptable monitor configuration.  This takes two
   * arguments, which are the DN of the configuration entry and the reason that
   * it is not acceptable.
   */
  public static final int MSGID_CONFIG_MONITOR_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 129;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a monitor from a configuration entry.  This takes two
   * arguments, which are the DN of the configuration entry and a message that
   * explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_MONITOR_CANNOT_CREATE_MONITOR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 130;



  /**
   * The message ID for the message that will be used if an entry below the
   * monitor base does not contain a valid monitor objectclass.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_MONITOR_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 131;



  /**
   * The message ID for the description of the monitor class name configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_MONITOR_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 132;



  /**
   * The message ID for the message that will be used if an entry below the
   * monitor base does not contain a value for the logger class name.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_MONITOR_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 133;



  /**
   * The message ID for the message that will be used if an entry below the
   * monitor base contains an invalid value for the class name.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_MONITOR_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 134;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server monitor but the associated class cannot be
   * instantiated as a monitor provider.  This takes three arguments, which are
   * the monitor class name, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_MONITOR_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 135;



  /**
   * The message ID for the description of the monitor enabled configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_MONITOR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 136;



  /**
   * The message ID for the message that will be used if an entry below the
   * monitor base does not contain a value for the enabled attribute.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_MONITOR_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 137;



  /**
   * The message ID for the message that will be used if an entry below the
   * monitor base has an invalid value for the enabled attribute.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_MONITOR_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 138;



  /**
   * The message ID for the message that will be used if the monitor class has
   * changed and will require administrative action to take effect.  This takes
   * three arguments, which are the old class name, the new class name, and the
   * DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_MONITOR_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 139;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server monitor provider.  This takes three
   * arguments, which are the class name for the monitor class, the DN of
   * the configuration entry, and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_MONITOR_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 140;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new monitor provider with a DN that matches the DN of a monitor that
   * already exists.  This takes a single argument, which is the DN of the
   * monitor configuration entry.
   */
  public static final int MSGID_CONFIG_MONITOR_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 141;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the base entry for the server connection handlers.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CONFIG_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 142;



  /**
   * The message ID for the message that will be used if the configuration base
   * entry for the set of connection handlers does not exist in the server.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 143;



  /**
   * The message ID for the message that will be used if a configuration entry
   * below the connection handler config base does not appear to contain a
   * connection handler configuration.  This takes a single argument, which is
   * the DN of the configuration entry.
   */
  public static final int
       MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 144;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to interact with a connection handler configuration
   * entry.  This takes two arguments, which are the DN of the connection
   * handler configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int
       MSGID_CONFIG_CONNHANDLER_ERROR_INTERACTING_WITH_CONNHANDLER_ENTRY =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 145;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that controls whether a connection handler is
   * enabled.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 146;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the class name for a connection
   * handler implementation.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 147;



  /**
   * The message ID for the message that will be used if a connection handler
   * configuration entry does not contain an attribute to indicate whether the
   * connection handler is enabled or disabled.  This takes a single argument,
   * which is the DN of the connection handler configuration entry.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 148;



  /**
   * The message ID for the message that will be used if a connection handler
   * configuration entry is marked disabled and therefore will not be processed.
   * This takes a single argument, which is the DN of the connection handler
   * configuration entry.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 149;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine whether a connection handler should be
   * enabled.  This takes two arguments, which are the DN of the connection
   * handler configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int
       MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 150;



  /**
   * The message ID for the message that will be used if a connection handler
   * configuration entry does not contain the name of the class providing the
   * connection handler implementation.  This takes a single argument, which is
   * the DN of the connection handler configuration entry.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 151;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the name of the class to use for a connection handler
   * implementation.  This takes two arguments, which are the DN of the
   * connection handler configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 152;



  /**
   * The message ID for the message that will be used if an error occurs while
   * load and instantiate a connection handler class.  This takes three
   * arguments, which are the name of the connection handler class, the DN of
   * the configuration entry, and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_CANNOT_INSTANTIATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 153;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initialize a connection handler from a configuration entry.  This takes
   * three arguments, which are the name of the connection handler class, the DN
   * of the configuration entry, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_CANNOT_INITIALIZE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 154;



  /**
   * The message ID for the message that will be used if a configuration handler
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 155;



  /**
   * The message ID for the message that will be used if a backend entry
   * contains an unacceptable configuration but does not provide any specific
   * details about the nature of the problem.  This takes a single argument,
   * which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 156;



  /**
   * The message ID for the message that will be used if a monitor provider
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_MONITOR_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 157;



  /**
   * The message ID for the message that will be used if a logger entry contains
   * an unacceptable configuration but does not provide any specific details
   * about the nature of the problem.  This takes a single argument, which is
   * the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_LOGGER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 158;



  /**
   * The message ID for the message that will be used if class defined for a
   * connection handler does not contain an actual connection handler
   * implementation.  This takes two arguments, which are the name of the class
   * and the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_CONNHANDLER_CLASS_NOT_CONNHANDLER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 159;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the base entry for the server matching rules.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_GET_MR_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 160;



  /**
   * The message ID for the message that will be used if the configuration base
   * entry for the set of matching rules does not exist in the server.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 161;



  /**
   * The message ID for the message that will be used if there are no entries
   * below the matching rule base entry.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_NO_MATCHING_RULES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 162;



  /**
   * The message ID for the message that will be used if a configuration entry
   * below the matching rule config base does not appear to contain a matching
   * rule configuration.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_ENTRY_DOES_NOT_HAVE_MR_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 163;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that controls whether a matching rule is enabled.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_ATTR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 164;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the class name for a matching rule
   * implementation.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_ATTR_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 165;



  /**
   * The message ID for the message that will be used if a matching rule
   * configuration entry does not contain an attribute to indicate whether the
   * matching rule is enabled or disabled.  This takes a single argument, which
   * is the DN of the matching rule configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 166;



  /**
   * The message ID for the message that will be used if a matching rule
   * configuration entry is marked disabled and therefore will not be processed.
   * This takes a single argument, which is the DN of the matching rule
   * configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 167;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine whether a matching rule should be
   * enabled.  This takes two arguments, which are the DN of the matching rule
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_CONFIG_SCHEMA_MR_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 168;



  /**
   * The message ID for the message that will be used if a matching rule
   * configuration entry does not contain the name of the class providing the
   * matching rule implementation.  This takes a single argument, which is
   * the DN of the matching rule configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 169;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the name of the class to use for a matching rule
   * implementation.  This takes two arguments, which are the DN of the matching
   * rule configuration entry and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_CANNOT_GET_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 170;



  /**
   * The message ID for the message that will be used if an error occurs while
   * load and instantiate a matching rule class.  This takes three arguments,
   * which are the name of the matching rule class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_CANNOT_INSTANTIATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 171;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initialize a matching rule from a configuration entry.  This takes three
   * arguments, which are the name of the matching rule class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_CANNOT_INITIALIZE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 172;



  /**
   * The message ID for the message that will be used if a matching rule read
   * from the server configuration conflicts with a matching rule already read
   * from another configuration entry.  This takes two arguments, which are the
   * DN of the configuration entry from which the matching rule configuration
   * was read, and a message explaining the nature of the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_MR_CONFLICTING_MR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 173;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the base entry for the server syntaxes.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_GET_SYNTAX_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 174;



  /**
   * The message ID for the message that will be used if the configuration base
   * entry for the set of syntaxes does not exist in the server.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 175;



  /**
   * The message ID for the message that will be used if there are no entries
   * below the syntax base entry.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_NO_SYNTAXES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 176;



  /**
   * The message ID for the message that will be used if a configuration entry
   * below the syntax config base does not appear to contain an attribute syntax
   * configuration.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int
       MSGID_CONFIG_SCHEMA_ENTRY_DOES_NOT_HAVE_SYNTAX_CONFIG =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 177;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that controls whether a syntax is enabled.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_ATTR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 178;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the class name for an attribute
   * syntax implementation.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_ATTR_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 179;



  /**
   * The message ID for the message that will be used if a syntax configuration
   * entry does not contain an attribute to indicate whether the syntax is
   * enabled or disabled.  This takes a single argument, which is the DN of the
   * syntax configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 180;



  /**
   * The message ID for the message that will be used if a syntax configuration
   * entry is marked disabled and therefore will not be processed.  This takes a
   * single argument, which is the DN of the syntax configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 181;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine whether a syntax should be enabled.
   * This takes two arguments, which are the DN of the syntax configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_SCHEMA_SYNTAX_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 182;



  /**
   * The message ID for the message that will be used if a syntax configuration
   * entry does not contain the name of the class providing the syntax
   * implementation.  This takes a single argument, which is the DN of the
   * syntax configuration entry.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 183;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the name of the class to use for a syntax
   * implementation.  This takes two arguments, which are the DN of the syntax
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_GET_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 184;



  /**
   * The message ID for the message that will be used if an error occurs while
   * load and instantiate a syntax class.  This takes three arguments, which are
   * the name of the syntax class, the DN of the configuration entry, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_INSTANTIATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 185;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initialize an attribute syntax from a configuration entry.  This takes
   * three arguments, which are the name of the syntax class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_INITIALIZE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 186;



  /**
   * The message ID for the message that will be used if a syntax read from the
   * server configuration conflicts with a syntax already read from another
   * configuration entry.  This takes two arguments, which are the DN of the
   * configuration entry from which the syntax configuration was read, and a
   * message explaining the nature of the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 187;



  /**
   * The message ID for the message that will be used if the schema directory
   * does not exist.  This takes a single argument, which is the path to the
   * schema directory.
   */
  public static final int MSGID_CONFIG_SCHEMA_NO_SCHEMA_DIR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 188;



  /**
   * The message ID for the message that will be used if the schema directory
   * is not a directory.  This takes a single argument, which is the path to the
   * schema directory.
   */
  public static final int MSGID_CONFIG_SCHEMA_DIR_NOT_DIRECTORY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 189;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to list the files in the schema directory.  This takes two
   * arguments, which are the path to the schema directory and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_LIST_FILES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 190;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open a schema file for reading.  This takes three arguments,
   * which are the name of the schema file, the path to the schema directory,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_OPEN_FILE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 191;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read an LDIF entry from a schema file.  This takes three
   * arguments, which are the name of the schema file, the path to the schema
   * directory, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 192;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse an attribute type definition.  This takes two arguments,
   * which are the name of the file from which the attribute type was read, and
   * a message explaining the problem that was encountered.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 193;



  /**
   * The message ID for the message that will be used if an attribute type
   * conflicts with another attribute type already read into the schema.  This
   * takes two arguments, which are the name of the file from which the
   * attribute type was read, and a message explaining the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_CONFLICTING_ATTR_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 194;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse an objectclass definition.  This takes two arguments, which
   * are the name of the file from which the objectclass was read, and a message
   * explaining the problem that was encountered.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_PARSE_OC =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 196;



  /**
   * The message ID for the message that will be used if an objectclass
   * conflicts with another objectclass already read into the schema.  This
   * takes two arguments, which are the name of the file from which the
   * objectclass was read, and a message explaining the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_CONFLICTING_OC =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 195;



  /**
   * The message ID for the description of the configuration attribute used to
   * specify the behavior for entries that do not contain exactly one structural
   * objectclass.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_STRUCTURAL_CLASS_BEHAVIOR =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 196;



  /**
   * The message ID for the message that will be used if the single structural
   * objectclass behavior attribute has an invalid value.  This takes two
   * arguments, which  are the DN of the configuration entry and a string
   * representation of the  exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 197;



  /**
   * The message ID for the description of the check schema configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_CHECK_SCHEMA =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 198;



  /**
   * The message ID for the message that will be used if the check schema
   * configuration attribute has an invalid value.  This takes two arguments,
   * which are the DN of the configuration entry and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_CHECK_SCHEMA =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 199;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to install the default entry cache.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTALL_DEFAULT_CACHE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 200;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the entry cache configuration entry.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_GET_CONFIG_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 201;



  /**
   * The message ID for the message that will be used if the entry cache
   * configuration entry does not exist.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_NO_CONFIG_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 202;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to register to watch for the entry cache configuration entry to
   * be created.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_REGISTER_ADD_LISTENER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 203;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to register to watch for the entry cache configuration entry to
   * be removed.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_ENTRYCACHE_CANNOT_REGISTER_DELETE_LISTENER =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 204;



  /**
   * The message ID for the message that will be used as the description for the
   * entry cache enabled attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_DESCRIPTION_CACHE_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 205;



  /**
   * The message ID for the message that will be used if the entry cache
   * configuration entry does not contain an enabled attribute.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 206;



  /**
   * The message ID for the message that will be used if the entry cache has
   * been explicitly disabled.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 207;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine whether the entry cache should be enabled.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int
       MSGID_CONFIG_ENTRYCACHE_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 208;



  /**
   * The message ID for the message that will be used as the description for the
   * entry cache class attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_DESCRIPTION_CACHE_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 209;



  /**
   * The message ID for the message that will be used if the entry cache
   * configuration entry does not contain a class name attribute.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 210;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the class to use for the entry cache.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_DETERMINE_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 211;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load the entry cache class.  This takes two arguments, which
   * are the class name and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_LOAD_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 212;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to instantiate the entry cache class.  This takes two arguments,
   * which are the class name and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTANTIATE_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 213;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the entry cache.  This takes two arguments, which
   * are the class name and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ENTRYCACHE_CANNOT_INITIALIZE_CACHE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 214;


  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to remove a child of a configuration entry.  This takes
   * three arguments, which are the DN of the child entry, the DN of the parent
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ENTRY_CANNOT_REMOVE_CHILD =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 215;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a name form definition.  This takes two arguments, which
   * are the name of the file from which the name form was read, and a
   * message explaining the problem that was encountered.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_PARSE_NAME_FORM =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 216;



  /**
   * The message ID for the message that will be used if a name form conflicts
   * with another name form already read into the schema.  This takes two
   * arguments, which are the name of the file from which the name form was
   * read, and a message explaining the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_CONFLICTING_NAME_FORM =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 217;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a DIT content rule definition.  This takes two arguments,
   * which are the name of the file from which the DIT content rule was read,
   * and a message explaining the problem that was encountered.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DCR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 218;



  /**
   * The message ID for the message that will be used if a DIT content rule
   * conflicts with another DIT content rule already read into the schema.  This
   * takes two arguments, which are the name of the file from which the DIT
   * content rule was read, and a message explaining the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_CONFLICTING_DCR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 219;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a DIT structure rule definition.  This takes two arguments,
   * which are the name of the file from which the DIT structure rule was read,
   * and a message explaining the problem that was encountered.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DSR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 220;



  /**
   * The message ID for the message that will be used if a DIT structure rule
   * conflicts with another DIT structure rule already read into the schema.
   * This takes two arguments, which are the name of the file from which the DIT
   * structure rule was read, and a message explaining the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_CONFLICTING_DSR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 221;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a matching rule use definition.  This takes two arguments,
   * which are the name of the file from which the matching rule use was read,
   * and a message explaining the problem that was encountered.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_PARSE_MRU =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 222;



  /**
   * The message ID for the message that will be used if a matching rule use
   * conflicts with another matching rule use already read into the schema.
   * This takes two arguments, which are the name of the file from which the
   * matching rule use was read, and a message explaining the conflict.
   */
  public static final int MSGID_CONFIG_SCHEMA_CONFLICTING_MRU =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 223;


  /**
   * The message ID for the message that will be used if no log rotation
   * policy is specified.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_ROTATION_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 224;


  /**
   * The message ID for the message that will be used if the size based
   * rotation policy is specified and no size limit is defined.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_SIZE_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 225;


  /**
   * The message ID for the message that will be used if the time limit based
   * rotation policy is specified and no time limit is defined.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_TIME_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 226;


  /**
   * The message ID for the message that will be used if an invalid
   * rotation policy is specified.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 227;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * alter the value of a read-only configuration attribute.  This takes a
   * single argument, which is the name of the attribute.
   */
  public static final int MSGID_CONFIG_ATTR_READ_ONLY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 228;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to get the base entry for the server plugins.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_PLUGIN_CANNOT_GET_CONFIG_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 229;



  /**
   * The message ID for the message that will be used if the configuration base
   * entry for the set of plugins does not exist in the server.  This does not
   * take any arguments.
   */
  public static final int MSGID_CONFIG_PLUGIN_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 230;



  /**
   * The message ID for the message that will be used if a configuration entry
   * below the plugin config base does not appear to contain a plugin
   * configuration.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int
       MSGID_CONFIG_PLUGIN_ENTRY_DOES_NOT_HAVE_PLUGIN_CONFIG =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 231;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to interact with a plugin configuration entry.  This
   * takes two arguments, which are the DN of the plugin configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_PLUGIN_ERROR_INTERACTING_WITH_PLUGIN_ENTRY =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 232;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that controls whether a plugin is enabled.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 233;



  /**
   * The message ID for the message that will be used if a plugin configuration
   * entry does not contain an attribute to indicate whether the plugin is
   * enabled or disabled.  This takes a single argument, which is the DN of the
   * plugin configuration entry.
   */
  public static final int MSGID_CONFIG_PLUGIN_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 234;



  /**
   * The message ID for the message that will be used if a plugin configuration
   * entry is marked disabled and therefore will not be processed.  This takes a
   * single argument, which is the DN of the plugin configuration entry.
   */
  public static final int MSGID_CONFIG_PLUGIN_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 235;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine whether a plugin should be enabled.
   * This takes two arguments, which are the DN of the plugin configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_PLUGIN_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 236;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the plugin type for a plugin
   * implementation.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_PLUGIN_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 237;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the class name for a plugin
   * implementation.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 238;



  /**
   * The message ID for the message that will be used if a plugin configuration
   * entry does not contain a set of plugin types.  This takes a single
   * argument, which is the DN of the plugin configuration entry.
   */
  public static final int MSGID_CONFIG_PLUGIN_NO_PLUGIN_TYPES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 239;



  /**
   * The message ID for the message that will be used if a plugin configuration
   * entry contains an invalid plugin type.  This takes two arguments, which are
   * the DN of the plugin configuration entry and the invalid plugin type.
   */
  public static final int MSGID_CONFIG_PLUGIN_INVALID_PLUGIN_TYPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 240;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of plugin types for a plugin definition.  This
   * takes two arguments, which are the DN of the plugin configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PLUGIN_CANNOT_GET_PLUGIN_TYPES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 241;



  /**
   * The message ID for the message that will be used if a plugin configuration
   * entry does not contain the name of the class providing the plugin
   * implementation.  This takes a single argument, which is the DN of the
   * plugin configuration entry.
   */
  public static final int MSGID_CONFIG_PLUGIN_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 242;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the name of the class to use for a plugin
   * implementation.  This takes two arguments, which are the DN of the plugin
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_PLUGIN_CANNOT_GET_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 243;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load and instantiate a plugin class.  This takes three arguments,
   * which are the name of the plugin class, the DN of the configuration entry,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PLUGIN_CANNOT_INSTANTIATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 244;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize a plugin from a configuration entry.  This takes three
   * arguments, which are the name of the plugin class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_PLUGIN_CANNOT_INITIALIZE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 245;



  /**
   * The message ID for the description of the notify abandoned operations
   * configuration attribute.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_NOTIFY_ABANDONED_OPERATIONS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 246;



  /**
   * The message ID for the message that will be used if the notify abandoned
   * operations configuration attribute has an invalid value.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_CORE_INVALID_NOTIFY_ABANDONED_OPERATIONS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 247;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the extended operation handler base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
//  public static final int MSGID_CONFIG_EXTOP_CANNOT_GET_BASE =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 248;



  /**
   * The message ID for the message that will be used if the extended operation
   * handler base entry does not exist in the Directory Server configuration.
   * This does not take any arguments.
   */
//  public static final int MSGID_CONFIG_EXTOP_BASE_DOES_NOT_EXIST =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 249;



  /**
   * The message ID for the message that will be used if an extended operation
   * handler configuration entry does not contain an acceptable handler
   * configuration.  This takes two arguments, which are the DN of the
   * configuration entry and the reason that it is not acceptable.
   */
//  public static final int MSGID_CONFIG_EXTOP_ENTRY_UNACCEPTABLE =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 250;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create an extended operation handler from a configuration entry.
   * This takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
//  public static final int MSGID_CONFIG_EXTOP_CANNOT_CREATE_HANDLER =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 251;



  /**
   * The message ID for the message that will be used if an entry below the
   * extended operation does not contain a valid objectclass.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
//  public static final int MSGID_CONFIG_EXTOP_INVALID_OBJECTCLASS =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 252;



  /**
   * The message ID for the description of the extended operation handler class
   * name configuration attribute.  This does not take any arguments.
   */
//  public static final int MSGID_CONFIG_EXTOP_DESCRIPTION_CLASS_NAME =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 253;



  /**
   * The message ID for the message that will be used if an entry below the
   * extended operation base does not contain a value for the class name.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
//  public static final int MSGID_CONFIG_EXTOP_NO_CLASS_NAME =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 254;



  /**
   * The message ID for the message that will be used if an entry below the
   * extended operation base contains an invalid value for the class name.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
//  public static final int MSGID_CONFIG_EXTOP_INVALID_CLASS_NAME =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 255;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server extended operation but the associated class
   * cannot be instantiated as an extended operation handler.  This takes three
   * arguments, which are the handler class name, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_EXTOP_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 256;



  /**
   * The message ID for the description of the extended operation handler
   * enabled configuration attribute.  This does not take any arguments.
   */
//  public static final int MSGID_CONFIG_EXTOP_DESCRIPTION_ENABLED =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 257;



  /**
   * The message ID for the message that will be used if an entry below the
   * extended operation base does not contain a value for the enabled attribute.
   * This takes a single argument, which is the DN of the configuration entry.
   */
//  public static final int MSGID_CONFIG_EXTOP_NO_ENABLED_ATTR =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 258;



  /**
   * The message ID for the message that will be used if an entry below the
   * extended operation base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
//  public static final int MSGID_CONFIG_EXTOP_INVALID_ENABLED_VALUE =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 259;



  /**
   * The message ID for the message that will be used if the extended operation
   * handler class has changed and will require administrative action to take
   * effect.  This takes three arguments, which are the old class name, the new
   * class name, and the DN of the associated configuration entry.
   */
//  public static final int MSGID_CONFIG_EXTOP_CLASS_ACTION_REQUIRED =
//            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 260;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server extended operation handler.  This takes
   * three arguments, which are the class name for the handler class, the DN of
   * the configuration entry, and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_EXTOP_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 261;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new extended operation handler with a DN that matches the DN of a
   * handler that already exists.  This takes a single argument, which is the DN
   * of the handler configuration entry.
   */
//  public static final int MSGID_CONFIG_EXTOP_EXISTS =
//            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 262;



  /**
   * The message ID for the message that will be used if an extended operation
   * handler entry contains an unacceptable configuration but does not provide
   * any specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
//  public static final int MSGID_CONFIG_EXTOP_UNACCEPTABLE_CONFIG =
//       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 263;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the SASL mechanism handler base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SASL_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 264;



  /**
   * The message ID for the message that will be used if the SASL mechanism
   * handler base entry does not exist in the Directory Server configuration.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SASL_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 265;



  /**
   * The message ID for the message that will be used if a SASL mechanism
   * handler configuration entry does not contain an acceptable handler
   * configuration.  This takes two arguments, which are the DN of the
   * configuration entry and the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_SASL_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 266;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a SASL mechanism handler from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_SASL_CANNOT_CREATE_HANDLER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 267;



  /**
   * The message ID for the message that will be used if an entry below the SASL
   * mechanism does not contain a valid objectclass.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SASL_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 268;



  /**
   * The message ID for the description of the SASL mechanism handler class name
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SASL_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 269;



  /**
   * The message ID for the message that will be used if an entry below the SASL
   * mechanism base does not contain a value for the class name.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SASL_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 270;



  /**
   * The message ID for the message that will be used if an entry below the SASL
   * mechanism base contains an invalid value for the class name.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SASL_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 271;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server SASL mechanism but the associated class cannot
   * be instantiated as a SASL mechanism handler.  This takes three arguments,
   * which are the handler class name, the DN of the configuration entry, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SASL_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 272;



  /**
   * The message ID for the description of the SASL mechanism handler enabled
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SASL_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 273;



  /**
   * The message ID for the message that will be used if an entry below the SASL
   * mechanism base does not contain a value for the enabled attribute.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SASL_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 274;



  /**
   * The message ID for the message that will be used if an entry below the SASL
   * mechanism base has an invalid value for the enabled attribute.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SASL_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 275;



  /**
   * The message ID for the message that will be used if the SASL mechanism
   * handler class has changed and will require administrative action to take
   * effect.  This takes three arguments, which are the old class name, the new
   * class name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_SASL_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 276;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server SASL mechanism handler.  This takes three
   * arguments, which are the class name for the handler class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_SASL_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 277;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new SASL mechanism handler with a DN that matches the DN of a
   * handler that already exists.  This takes a single argument, which is the DN
   * of the handler configuration entry.
   */
  public static final int MSGID_CONFIG_SASL_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 278;



  /**
   * The message ID for the message that will be used if an SASL mechanism
   * handler entry contains an unacceptable configuration but does not provide
   * any specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SASL_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 279;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new configuration entry with a DN that already exists.  This takes a
   * single argument, which is the DN of the entry to add.
   */
  public static final int MSGID_CONFIG_FILE_ADD_ALREADY_EXISTS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 280;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new configuration entry with a DN that does not have a parent.  This
   * takes a single argument, which is the DN of the entry to add.
   */
  public static final int MSGID_CONFIG_FILE_ADD_NO_PARENT_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 281;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new configuration entry for which the parent entry does not exist.
   * This takes two arguments, which are the DN of the entry to add and the DN
   * of its parent.
   */
  public static final int MSGID_CONFIG_FILE_ADD_NO_PARENT=
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 282;



  /**
   * The message ID for the message that will be used if an attempt to add a new
   * configuration entry is rejected by one of the add listeners.  This takes
   * three arguments, which are the DN of the entry to add, the DN of its
   * parent, and the unacceptable reason given by the add listener.
   */
  public static final int MSGID_CONFIG_FILE_ADD_REJECTED_BY_LISTENER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 283;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to add a new configuration entry.  This takes three
   * arguments, which are the DN of the entry to add, the DN of its  parent, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_ADD_FAILED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 284;



  /**
   * The message ID for the message that will be used if a request is made to
   * delete a nonexistent config entry.  This takes a single argument, which is
   * the DN of the target entry.
   */
  public static final int MSGID_CONFIG_FILE_DELETE_NO_SUCH_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 285;



  /**
   * The message ID for the message that will be used if a request is made to
   * delete config entry that has one or more children.  This takes a single
   * argument, which is the DN of the target entry.
   */
  public static final int MSGID_CONFIG_FILE_DELETE_HAS_CHILDREN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 286;



  /**
   * The message ID for the message that will be used if a request is made to
   * delete a config entry that does not have a parent.  This takes a single
   * argument, which is the DN of the target entry.
   */
  public static final int MSGID_CONFIG_FILE_DELETE_NO_PARENT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 287;



  /**
   * The message ID for the message that will be used if a configuration delete
   * is rejected by one of the parent's delete listeners.  This takes three
   * arguments, which are the DN of the target entry, the DN of its parent, and
   * the unacceptable reason.
   */
  public static final int MSGID_CONFIG_FILE_DELETE_REJECTED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 288;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to remove a configuration entry.  This takes three
   * arguments, which are the DN of the entry to remove, the DN of its  parent,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_DELETE_FAILED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 289;



  /**
   * The message ID for the message that will be used if a request is made to
   * modify a nonexistent config entry.  This takes a single argument, which is
   * the DN of the target entry.
   */
  public static final int MSGID_CONFIG_FILE_MODIFY_NO_SUCH_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 290;



  /**
   * The message ID for the message that will be used if a configuration modify
   * is rejected by one of its change listeners.  This takes two arguments,
   * which are the DN of the target entry and the unacceptable reason.
   */
  public static final int MSGID_CONFIG_FILE_MODIFY_REJECTED_BY_CHANGE_LISTENER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 291;



  /**
   * The message ID for the message that will be used if a configuration modify
   * is rejected by one of the registered configurable components.  This takes
   * two arguments, which are the DN of the target entry and the unacceptable
   * reason.
   */
  public static final int MSGID_CONFIG_FILE_MODIFY_REJECTED_BY_COMPONENT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 292;



  /**
   * The message ID for the message that will be used if a configuration search
   * fails because the base entry does not exist.  This takes a single argument,
   * which is the base DN.
   */
  public static final int MSGID_CONFIG_FILE_SEARCH_NO_SUCH_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 293;



  /**
   * The message ID for the message that will be used if a configuration search
   * fails because it has an invalid scope.  This takes a single argument, which
   * is a string representation of the invalid scope.
   */
  public static final int MSGID_CONFIG_FILE_SEARCH_INVALID_SCOPE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 294;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create a temporary file to hold the updated configuration
   * archive.  This takes two arguments, which are the name of the temporary
   * archive file and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_CREATE_TEMP_ARCHIVE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 295;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to copy the previous archived configurations into the new
   * archive.  This takes three arguments, which are the name of the previous
   * archive, the name of the temporary archive file, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_COPY_EXISTING_ARCHIVE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 296;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to copy the running configuration into the new archive.  This
   * takes three arguments, which are the name of the temporary archive file,
   * the path of the running configuration file, and a string  representation of
   * the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_COPY_CURRENT_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 297;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to rename the temporary archived configuration file with the
   * permanent name.  This takes two arguments, which are the temporary and
   * permanent names of the archived configurations.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_RENAME_TEMP_ARCHIVE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 298;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to replace the archived configuration.  This takes three
   * arguments, which are the temporary and permanent names of the archived
   * configurations, a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_REPLACE_ARCHIVE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 299;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to write the new configuration to a temporary file.  This takes
   * two arguments, which are the path of the temporary file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_EXPORT_NEW_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 300;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to rename the temporary copy of the new configuration to the
   * permanent name.  This takes three arguments, which are the temporary and
   * permanent names of the configuration, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_WRITE_CANNOT_RENAME_NEW_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 301;



  /**
   * The message ID for the message that will be used if a user attempts to
   * perform a modify DN in the configuration.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_FILE_MODDN_NOT_ALLOWED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 302;


  /**
   * The message ID for the suppress internal operations attribute.
   */
  public static final int MSGID_CONFIG_LOGGER_SUPPRESS_INTERNAL_OPERATIONS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 303;

  /**
   * The message ID for invalid suppress internal operations values.
   */
  public static final int
    MSGID_CONFIG_LOGGER_INVALID_SUPPRESS_INT_OPERATION_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 304;



  /**
   * The message ID for the message that will be used as the description for the
   * configuration attribute that specifies the set of base DNs for a backend.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 305;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not specify the set of base DNs for that backend.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_NO_BASE_DNS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 306;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine the set of base DNs for a backend.
   * This takes two arguments, which are the DN of the backend configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 307;



  /**
   * The message ID for the message that will be used as the description for the
   * key manager provider enabled attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 313;



  /**
   * The message ID for the message that will be used if the key manager
   * provider configuration entry does not contain an enabled attribute.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 314;



  /**
   * The message ID for the message that will be used as the description for the
   * key manager provider class attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 317;



  /**
   * The message ID for the message that will be used as the description for the
   * trust manager provider enabled attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 328;



  /**
   * The message ID for the message that will be used if the trust manager
   * provider configuration entry does not contain an enabled attribute.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 329;



  /**
   * The message ID for the message that will be used as the description for the
   * trust manager provider class attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 332;



  /**
   * The message ID for the message that will be used as the description for the
   * certificate mapper enabled attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 347;



  /**
   * The message ID for the message that will be used if the certificate mapper
   * configuration entry does not contain an enabled attribute.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 348;



  /**
   * The message ID for the message that will be used as the description for the
   * certificate mapper class attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 351;



  /**
   * The message ID for the message that will be used if no log retention
   * policy is specified.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_RETENTION_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_WARNING | 357;


  /**
   * The message ID for the message that will be used if an invalid retention
   * policy is specified.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 358;


  /**
   * The message ID for the message that will be used if no number of files
   * are specified.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_NUMBER_OF_FILES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 359;

  /**
   * The message ID for the message that will be used if no disk space needed
   * is specified.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_DISK_SPACE_USED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 360;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the password storage scheme base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWSCHEME_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 363;



  /**
   * The message ID for the message that will be used if the password storage
   * scheme base entry does not exist in the Directory Server configuration.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWSCHEME_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 364;



  /**
   * The message ID for the message that will be used if a password storage
   * scheme configuration entry does not contain an acceptable configuration.
   * This takes two arguments, which are the DN of the configuration entry and
   * the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_PWSCHEME_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 365;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a password storage scheme from a configuration entry.
   * This takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_PWSCHEME_CANNOT_CREATE_SCHEME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 366;



  /**
   * The message ID for the message that will be used if an entry below the
   * password storage scheme base does not contain a valid storage scheme
   * objectclass.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_PWSCHEME_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 367;



  /**
   * The message ID for the description of the password storage scheme class
   * name configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWSCHEME_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 368;



  /**
   * The message ID for the message that will be used if an entry below the
   * password storage scheme base does not contain a value for the class name.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWSCHEME_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 369;



  /**
   * The message ID for the message that will be used if an entry below the
   * password storage scheme base contains an invalid value for the class name.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWSCHEME_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 370;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server password storage scheme but the associated class
   * cannot be instantiated as a password storage scheme.  This takes three
   * arguments, which are the storage scheme class name, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_PWSCHEME_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 371;



  /**
   * The message ID for the description of the password storage scheme enabled
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWSCHEME_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 372;



  /**
   * The message ID for the message that will be used if an entry below the
   * password storage scheme base does not contain a value for the enabled
   * attribute.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_PWSCHEME_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 373;



  /**
   * The message ID for the message that will be used if an entry below the
   * password storage scheme base has an invalid value for the enabled
   * attribute.  This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWSCHEME_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 374;



  /**
   * The message ID for the message that will be used if the password storage
   * scheme class has changed and will require administrative action to take
   * effect.  This takes three arguments, which are the old class name, the new
   * class name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_PWSCHEME_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 375;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server password storage scheme.  This takes three
   * arguments, which are the class name for the storage scheme class, the DN of
   * the configuration entry, and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_PWSCHEME_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 376;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new password storage scheme with a DN that matches the DN of a scheme
   * that already exists.  This takes a single argument, which is the DN of the
   * storage scheme configuration entry.
   */
  public static final int MSGID_CONFIG_PWSCHEME_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 377;



  /**
   * The message ID for the message that will be used if a password storage
   * scheme entry contains an unacceptable configuration but does not provide
   * any specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWSCHEME_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 378;



  /**
   * The message ID for the message that will be used if the plugin class has
   * changed and will require administrative action to take effect.  This takes
   * three arguments, which are the old class name, the new class name, and the
   * DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_PLUGIN_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 379;


  /**
   * The message ID for the message that will be used if the free disk based
   * retention policy is specified and no size limit is defined.
   */
  public static final int MSGID_CONFIG_LOGGER_NO_FREE_DISK_SPACE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 380;


  /**
   * The message ID for the message that will be used if the free disk based
   * retention policy is and the server is not running on Java 6.
   */
  public static final int MSGID_CONFIG_LOGGER_INVALID_JAVA5_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 381;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that holds the backend ID.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 382;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not contain the backend ID attribute.  This takes a single
   * argument, which is the DN of the backend configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_NO_BACKEND_ID =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 383;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry contains a backend ID that conflicts with the ID for another backend.
   * This takes a single argument, which is the DN of the backend configuration
   * entry.
   */
  public static final int MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 384;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine the backend ID for a backend.  This
   * takes two arguments, which are the DN of the backend configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 385;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the MAC provider for the config backup.  This takes
   * two arguments, which are the name of the desired MAC algorithm and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_GET_MAC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 386;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the message digest for the config backup.  This takes
   * two arguments, which are the name of the desired digest algorithm and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_GET_DIGEST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 387;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create the archive file for a config backup.  This takes
   * three arguments, which are the name of the archive file, the path to the
   * archive directory, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_CREATE_ARCHIVE_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 388;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the cipher for the config backup.  This takes two
   * arguments, which are the name of the desired cipher algorithm and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_GET_CIPHER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 389;



  /**
   * The message ID for the message that will be used for the message containing
   * the comment to include in the config archive zip.  This takes two
   * arguments, which are the Directory Server product name and the backup ID.
   */
  public static final int MSGID_CONFIG_BACKUP_ZIP_COMMENT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 390;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the path to the configuration file.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_CONFIG_BACKUP_CANNOT_DETERMINE_CONFIG_FILE_LOCATION =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 391;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to back up a config file.  This takes two arguments, which are
   * the name of the config file and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_BACKUP_CONFIG_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 392;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to back up the archived configs.  This takes two arguments,
   * which are the name of the config file and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_BACKUP_ARCHIVED_CONFIGS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 393;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to close the output stream for the config archive.  This takes
   * three arguments, which are the name of the config archive file, the path
   * to the directory containing that file, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_CLOSE_ZIP_STREAM =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 394;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the backup descriptor with information about the
   * config backup.  This takes two arguments, which are the path to the backup
   * descriptor file and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 395;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * restore config backup but the requested backup could not be found.  This
   * takes two arguments, which are the backup ID and the path to the backup
   * directory.
   */
  public static final int MSGID_CONFIG_RESTORE_NO_SUCH_BACKUP =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 396;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * restore config backup but it cannot be determined which archive file holds
   * that backup.  This takes two arguments, which are the backup ID and the
   * path to the backup directory.
   */
  public static final int MSGID_CONFIG_RESTORE_NO_BACKUP_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 397;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * restore config backup but the archive file does not exist.  This takes two
   * arguments, which are the backup ID and the expected path to the archive
   * file.
   */
  public static final int MSGID_CONFIG_RESTORE_NO_SUCH_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 398;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether the backup archive exists.  This takes three
   * arguments, which are the backup ID, the expected path to the backup
   * archive, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_CHECK_FOR_ARCHIVE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 399;



  /**
   * The message ID for the message that will be used if a config backup is
   * hashed but the digest algorithm is not known.  This takes a single
   * argument, which is the backup ID.
   */
  public static final int MSGID_CONFIG_RESTORE_UNKNOWN_DIGEST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 400;



  /**
   * The message ID for the message that will be used if a config backup has a
   * hash with an unknown or unsupported digest algorithm.  This takes two
   * arguments, which are the backup ID and the digest algorithm.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_GET_DIGEST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 401;



  /**
   * The message ID for the message that will be used if a config backup is
   * signed but the MAC algorithm is not known.  This takes a single argument,
   * which is the backup ID.
   */
  public static final int MSGID_CONFIG_RESTORE_UNKNOWN_MAC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 402;



  /**
   * The message ID for the message that will be used if a config backup has a
   * signature with an unknown or unsupported MAC algorithm.  This takes two
   * arguments, which are the backup ID and the MAC algorithm.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_GET_MAC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 403;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the file containing the backup archive.  This takes three
   * arguments, which are the backup ID, the path to the backup file, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_OPEN_BACKUP_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 404;



  /**
   * The message ID for the message that will be used if a config backup is
   * encrypted but the cipher is not known.  This takes a single argument, which
   * is the backup ID.
   */
  public static final int MSGID_CONFIG_RESTORE_UNKNOWN_CIPHER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 405;



  /**
   * The message ID for the message that will be used if a config backup is
   * encrypted with an unknown or unsupported cipher.  This takes two arguments,
   * which are the backup ID and the cipher algorithm.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_GET_CIPHER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 406;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to backup the current config before the restore.  This takes four
   * arguments, which are the backup ID, the path to the current config
   * directory, the path to the backup config directory, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_BACKUP_EXISTING_CONFIG =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 407;



  /**
   * The message ID for the message that will be used if an error occurs during
   * the config restore process but the original config was restored to its
   * original location.  This takes a single argument, which is the path to the
   * config directory.
   */
  public static final int MSGID_CONFIG_RESTORE_RESTORED_OLD_CONFIG =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 408;



  /**
   * The message ID for the message that will be used if an error occurs while
   * the config restore process and the original config files could not be
   * moved back into place.  This takes a single argument, which is the path
   * to the directory containing the original config files.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_RESTORE_OLD_CONFIG =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 409;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a new directory to hold the restored config files.  This
   * takes three arguments, which are the backup ID, the desired path for the
   * config directory, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_CREATE_CONFIG_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 410;



  /**
   * The message ID for the message that will be used if an error occurs while
   * the config restore process but the old config files were saved in an
   * alternate directory.  This takes a single argument, which is the path
   * to the directory containing the original config files.
   */
  public static final int MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 411;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the next entry from the config archive.  This takes three
   * arguments, which are the backup ID, the path to the config archive, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_GET_ZIP_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 412;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a config file from the backup archive.  This takes three
   * arguments, which are the backup ID, the path to the file that could not be
   * created, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_CREATE_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 413;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read a config file from the archive or write it to disk.
   * This takes three arguments, which are the backup ID, the name of the file
   * being processed, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 414;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to close the zip stream used to read from the archive.  This takes
   * three arguments, which are the backup ID, the path to the backup archive,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 415;



  /**
   * The message ID for the message that will be used if the unsigned hash of
   * the config backup matches the expected value.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_RESTORE_UNSIGNED_HASH_VALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 416;



  /**
   * The message ID for the message that will be used if the unsigned hash of
   * the config backup does not match the expected value.  This takes a single
   * argument, which is the backup ID.
   */
  public static final int MSGID_CONFIG_RESTORE_UNSIGNED_HASH_INVALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 417;



  /**
   * The message ID for the message that will be used if the signed hash of the
   * config backup matches the expected value.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_RESTORE_SIGNED_HASH_VALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 418;



  /**
   * The message ID for the message that will be used if the signed hash of the
   * config backup does not match the expected value.  This takes a single
   * argument, which is the backup ID.
   */
  public static final int MSGID_CONFIG_RESTORE_SIGNED_HASH_INVALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 419;



  /**
   * The message ID for the message that will be used to indicate that the
   * backup verification process completed successfully.  This takes two
   * arguments, which are the backup ID and the path to the backup directory.
   */
  public static final int MSGID_CONFIG_RESTORE_VERIFY_SUCCESSFUL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 420;



  /**
   * The message ID for the message that will be used to indicate that the
   * backup verification process completed successfully.  This takes two
   * arguments, which are the backup ID and the path to the backup directory.
   */
  public static final int MSGID_CONFIG_RESTORE_SUCCESSFUL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 421;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to acquire a shared lock for a Directory Server backend.  This
   * takes two arguments, which are the backend ID for the associated backend
   * and a message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 422;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a shared lock for a Directory Server backend.  This
   * takes two arguments, which are the backend ID for the associated backend
   * and a message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 423;



  /**
   * The message ID for the message that will be used as the header written to
   * the top of the Directory Server configuration file whenever the
   * configuration is updated.  It does not take any arguments.
   */
  public static final int MSGID_CONFIG_FILE_HEADER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 424;



  /**
   * The message ID for the description of the add missing RDN attributes
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_ADD_MISSING_RDN_ATTRS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 425;



  /**
   * The message ID for the message that will be used if the add missing RDN
   * attributes configuration attribute has an invalid value.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_ADD_MISSING_RDN_ATTRS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 426;



  /**
   * The message ID for the description of the server error result code
   * configuration attribute.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_SERVER_ERROR_RESULT_CODE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 427;



  /**
   * The message ID for the message that will be used if the server error result
   * code configuration attribute has an invalid value.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_SERVER_ERROR_RESULT_CODE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 428;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the identity mapper base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_IDMAPPER_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 429;



  /**
   * The message ID for the message that will be used if the identity mapper
   * base entry does not exist in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_IDMAPPER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 430;



  /**
   * The message ID for the message that will be used if an identity mapper
   * configuration entry does not contain an acceptable configuration.  This
   * takes two arguments, which are the DN of the configuration entry and the
   * reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_IDMAPPER_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 431;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create an identity mapper from a configuration entry.  This takes
   * two arguments, which are the DN of the configuration entry and a message
   * that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_IDMAPPER_CANNOT_CREATE_MAPPER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 432;



  /**
   * The message ID for the message that will be used if an entry below the
   * identity mapper base does not contain a valid identity mapper objectclass.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_IDMAPPER_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 433;



  /**
   * The message ID for the description of the identity mapper class name
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_IDMAPPER_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 434;



  /**
   * The message ID for the message that will be used if an entry below the
   * identity mapper base does not contain a value for the class name.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_IDMAPPER_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 435;



  /**
   * The message ID for the message that will be used if an entry below the
   * identity mapper base contains an invalid value for the class name.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_IDMAPPER_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 436;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server identity mapper but the associated class cannot
   * be instantiated as an identity mapper.  This takes three arguments, which
   * are the identity mapper class name, the DN of the configuration entry, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_IDMAPPER_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 437;



  /**
   * The message ID for the description of the identity mapper enabled
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_IDMAPPER_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 438;



  /**
   * The message ID for the message that will be used if an entry below the
   * identity mapper base does not contain a value for the enabled attribute.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_IDMAPPER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 439;



  /**
   * The message ID for the message that will be used if an entry below the
   * identity mapper base has an invalid value for the enabled attribute.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_IDMAPPER_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 440;



  /**
   * The message ID for the message that will be used if the identity mapper
   * class has changed and will require administrative action to take effect.
   * This takes three arguments, which are the old class name, the new class
   * name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_IDMAPPER_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 441;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server identity mapper.  This takes three
   * arguments, which are the class name for the identity mapper class, the DN
   * of the configuration entry, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_IDMAPPER_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 442;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new identity mapper with a DN that matches the DN of a mapper that
   * already exists.  This takes a single argument, which is the DN of the
   * identity mapper configuration entry.
   */
  public static final int MSGID_CONFIG_IDMAPPER_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 443;



  /**
   * The message ID for the message that will be used if an identity mapper
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_IDMAPPER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 444;



  /**
   * The message ID for the description of the proxied auth identity mapper DN
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_PROXY_MAPPER_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 445;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the proxied auth identity mapper DN.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_PROXY_MAPPER_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 446;



  /**
   * The message ID for the message that will be used if there is no identity
   * mapper entry for the DN specified for use with the proxied auth control.
   * This takes two arguments, which are the provided identity mapper DN and the
   * configuration entry DN.
   */
  public static final int MSGID_CONFIG_CORE_NO_PROXY_MAPPER_FOR_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 447;



  /**
   * The message ID for the message that will be used if no proxied
   * authorization identity mapper is configured in the Directory Server.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_IDMAPPER_NO_PROXY_MAPPER_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 448;



  /**
   * The message ID for the message that will be used if the proxied
   * authorization identity mapper configured for use with the Directory Server
   * does not reference a valid identity mapper.  This takes a single argument,
   * which is the DN of the configured identity mapper.
   */
  public static final int MSGID_CONFIG_IDMAPPER_INVALID_PROXY_MAPPER_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 449;



  /**
   * The message ID for the description of the server size limit configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_SIZE_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 450;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server size limit.  This takes two arguments, which
   * are the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_SIZE_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 451;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the configuration base entry for the Directory
   * Server synchronization providers.  This takes a single argument, which is
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SYNCH_CANNOT_GET_CONFIG_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 452;



  /**
   * The message ID for the message that will be used if the Directory Server
   * synchronization provider configuration base entry does not exist.  This
   * does not take any arguments.
   */
  public static final int MSGID_CONFIG_SYNCH_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 453;



  /**
   * The message ID for the message that will be used if an entry below the
   * synchronization provider base does not contain the appropriate objectclass.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int
       MSGID_CONFIG_SYNCH_ENTRY_DOES_NOT_HAVE_PROVIDER_CONFIG =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 454;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether a synchronization provider had the appropriate
   * objectclass.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_CONFIG_SYNCH_CANNOT_CHECK_FOR_PROVIDER_CONFIG_OC =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 455;



  /**
   * The message ID for the message that will be used as the description of the
   * enabled configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 456;



  /**
   * The message ID for the message that will be used if a synchronization
   * provider entry does not contain the enabled attribute.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SYNCH_PROVIDER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 457;



  /**
   * The message ID for the message that will be used if a synchronization
   * provider is configured as disabled.  This takes a single argument, which is
   * the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SYNCH_PROVIDER_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 458;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine whether a configuration provider should be enabled.
   * This takes two arguments, which are the DN of the configuration entry and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_ENABLED_STATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 459;



  /**
   * The message ID for the message that will be used as the description for the
   * class attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 460;



  /**
   * The message ID for the message that will be used if a synchronization
   * provider configuration entry does not contain the class attribute.  It
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_SYNCH_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 461;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve determine the synchronization provider class.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 462;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load the synchronization provider class.  This takes three
   * arguments, which are the name of the class, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 463;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create an instance of a Directory Server configuration
   * provider.  This takes three arguments, which are the name of the class,
   * the DN of the configuration entry, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 464;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the synchronization provider.  This takes two
   * arguments, which are the DN of the configuration entry and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 465;



  /**
   * The message ID for the message that will be used if the synchronization
   * provider configuration has changed so that it should be disabled on
   * restart.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_SYNCH_PROVIDER_HAS_BEEN_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 466;



  /**
   * The message ID for the message that will be used if the synchronization
   * provider configuration has changed so that it should use a different class
   * on restart.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_SYNCH_PROVIDER_CLASS_CHANGED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 467;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the writability mode for a backend.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 468;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not define a writability mode attribute.  This takes a single
   * argument, which is the DN of the backend configuration entry.
   */
  public static final int MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 469;



  /**
   * The message ID for the message that will be used if a backend has an
   * invalid writability mode.  This takes two arguments, which are the DN of
   * the backend configuration entry and the invalid writability mode value.
   */
  public static final int MSGID_CONFIG_BACKEND_INVALID_WRITABILITY_MODE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 470;



  /**
   * The message ID for the message that will be used if an unexpected problem
   * occurs while attempting to determine the writability mode for a backend.
   * This takes two arguments, which are the DN of the backend configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 471;



  /**
   * The message ID for the description of the server writability mode
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_WRITABILITY_MODE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 472;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server writability mode.  This takes two arguments,
   * which are the DN of the configuration entry and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 473;



  /**
   * The message ID for the description of the server bind with DN requires
   * password configuration attribute.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_BIND_WITH_DN_REQUIRES_PW =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 474;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server configuration for simple binds containing a DN
   * but no password.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_BIND_WITH_DN_REQUIRES_PW =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 475;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the password validator base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 476;



  /**
   * The message ID for the message that will be used if the password validator
   * base entry does not exist in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 477;



  /**
   * The message ID for the message that will be used if a password validator
   * configuration entry does not contain an acceptable configuration.  This
   * takes two arguments, which are the DN of the configuration entry and the
   * reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 478;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a password validator from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_CANNOT_CREATE_VALIDATOR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 479;



  /**
   * The message ID for the message that will be used if an entry below the
   * password validator base does not contain a valid validator objectclass.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 480;



  /**
   * The message ID for the description of the password validator class name
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 481;



  /**
   * The message ID for the message that will be used if an entry below the
   * password validator base does not contain a value for the class name.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 482;



  /**
   * The message ID for the message that will be used if an entry below the
   * password validator base contains an invalid value for the class name.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 483;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server password validator but the associated class
   * cannot be instantiated as a password validator.  This takes three
   * arguments, which are the validator class name, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 484;



  /**
   * The message ID for the description of the password validator enabled
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 485;



  /**
   * The message ID for the message that will be used if an entry below the
   * password validator base does not contain a value for the enabled attribute.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 486;



  /**
   * The message ID for the message that will be used if an entry below the
   * password validator base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 487;



  /**
   * The message ID for the message that will be used if the password validator
   * class has changed and will require administrative action to take effect.
   * This takes three arguments, which are the old class name, the new class
   * name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 488;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server password validator.  This takes three
   * arguments, which are the class name for the validator class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 489;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new password validator with a DN that matches the DN of a scheme that
   * already exists.  This takes a single argument, which is the DN of the
   * validator configuration entry.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 490;



  /**
   * The message ID for the message that will be used if a password validator
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWVALIDATOR_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 491;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the password generator base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 492;



  /**
   * The message ID for the message that will be used if the password generator
   * base entry does not exist in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 493;



  /**
   * The message ID for the message that will be used if a password generator
   * configuration entry does not contain an acceptable configuration.  This
   * takes two arguments, which are the DN of the configuration entry and the
   * reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 494;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a password generator from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_CANNOT_CREATE_GENERATOR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 495;



  /**
   * The message ID for the message that will be used if an entry below the
   * password generator base does not contain a valid generator objectclass.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 496;



  /**
   * The message ID for the description of the password generator class name
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 497;



  /**
   * The message ID for the message that will be used if an entry below the
   * password generator base does not contain a value for the class name.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 498;



  /**
   * The message ID for the message that will be used if an entry below the
   * password generator base contains an invalid value for the class name.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 499;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server password generator but the associated class
   * cannot be instantiated as a password generator.  This takes three
   * arguments, which are the generator class name, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 500;



  /**
   * The message ID for the description of the password generator enabled
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 501;



  /**
   * The message ID for the message that will be used if an entry below the
   * password generator base does not contain a value for the enabled attribute.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 502;



  /**
   * The message ID for the message that will be used if an entry below the
   * password generator base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 503;



  /**
   * The message ID for the message that will be used if the password generator
   * class has changed and will require administrative action to take effect.
   * This takes three arguments, which are the old class name, the new class
   * name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 504;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server password generator.  This takes three
   * arguments, which are the class name for the generator class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 505;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new password generator with a DN that matches the DN of a scheme that
   * already exists.  This takes a single argument, which is the DN of the
   * generator configuration entry.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 506;



  /**
   * The message ID for the message that will be used if a password generator
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_PWGENERATOR_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 507;



  /**
   * The message ID for the description of the default password policy DN
   * configuration attribute.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_CORE_DESCRIPTION_DEFAULT_PWPOLICY_DN =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 508;



  /**
   * The message ID for the message that will be used if the root configuration
   * entry does not specify the DN of the default password policy.  This takes a
   * single argument, which is the DN of the root configuration entry.
   */
  public static final int MSGID_CONFIG_CORE_NO_DEFAULT_PWPOLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 509;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server configuration for the default password policy
   * DN.  This takes two arguments, which are the DN of the configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_DEFAULT_PWPOLICY_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 510;



  /**
   * The message ID for the message that will be used if the DN specified as
   * that of the default password policy doesn't refer to a valid policy
   * configuration entry.  This takes a single argument, which is the provided
   * default password policy DN.
   */
  public static final int MSGID_CONFIG_CORE_NO_SUCH_PWPOLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 511;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the password policy base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_PWPOLICY_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 512;



  /**
   * The message ID for the message that will be used if the password policy
   * base entry does not exist in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_PWPOLICY_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 513;



  /**
   * The message ID for the message that will be used if no password policy
   * configuration entries have been defined.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWPOLICY_NO_POLICIES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 514;



  /**
   * The message ID for the message that will be used if a password policy
   * configuration entry is invalid.  This takes two arguments, which are the DN
   * of the invalid password policy configuration entry and a message that
   * explains the problem with the entry.
   */
  public static final int MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 515;



  /**
   * The message ID for the message that will be used if the default password
   * policy entry does not exist in the configuration.  This takes a single
   * argument, which is the DN of the configuration entry for the default
   * password policy.
   */
  public static final int MSGID_CONFIG_PWPOLICY_MISSING_DEFAULT_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 516;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove the default password policy configuration entry.  This takes a
   * single argument, which is the DN of the entry to remove.
   */
  public static final int MSGID_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 517;



  /**
   * The message ID for the message that will be used to indicate that a
   * password policy configuration entry has been removed.
   */
  public static final int MSGID_CONFIG_PWPOLICY_REMOVED_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 518;


  /**
   * The message ID for the message that will be used if an error occurs
   * while attempting to retrieve the access control configuration
   * entry. This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_AUTHZ_CANNOT_GET_ENTRY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 519;



  /**
   * The message ID for the message that will be used if the Directory
   * Server access control configuration entry does not exist. This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 520;



  /**
   * The message ID for the message that will be used if the access
   * control configuration entry does not contain the appropriate
   * objectclass. This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int
       MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_HAVE_OBJECT_CLASS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 521;



  /**
   * The message ID for the message that will be used as the description
   * of the access control enabled configuration attribute. This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_AUTHZ_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 523;



  /**
   * The message ID for the message that will be used if the access
   * control configuration entry does not contain the enabled attribute.
   * This takes a single argument, which is the DN of the configuration
   * entry.
   */
  public static final int MSGID_CONFIG_AUTHZ_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 524;



  /**
   * The message ID for the message that will be used if access control
   * is disabled. This message does not have any arguments.
   */
  public static final int MSGID_CONFIG_AUTHZ_DISABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 525;



  /**
   * The message ID for the message that will be used if access control
   * is enabled. This takes a single argument, which is the name of the
   * access control handler class.
   */
  public static final int MSGID_CONFIG_AUTHZ_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_NOTICE | 526;



  /**
   * The message ID for the message that will be used if an error occurs
   * while attempting to determine whether access control should be
   * enabled. This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int
       MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_ENABLED_STATE =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 527;



  /**
   * The message ID for the message that will be used as the description
   * for the class attribute. This does not take any arguments.
   */
  public static final int MSGID_CONFIG_AUTHZ_DESCRIPTION_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 528;



  /**
   * The message ID for the message that will be used if the access
   * control configuration entry does not contain the class attribute.
   * It takes a single argument, which is the DN of the configuration
   * entry.
   */
  public static final int MSGID_CONFIG_AUTHZ_NO_CLASS_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 529;



  /**
   * The message ID for the message that will be used if an error occurs
   * while attempting to determine the access control handler class.
   * This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 530;



  /**
   * The message ID for the message that will be used if an error occurs
   * while attempting to load the access control handler class. This
   * takes three arguments, which are the name of the class, the DN of
   * the configuration entry, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_AUTHZ_UNABLE_TO_LOAD_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 531;



  /**
   * The message ID for the message that will be used if the loaded
   * access control handler class does not implement the correct
   * interface. This takes three arguments, which are the name of the
   * class, the DN of the configuration entry, the name of the access
   * control handler interface, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_AUTHZ_BAD_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 532;



  /**
   * The message ID for the message that will be used if an error occurs
   * while attempting to create an instance of an access control
   * handler. This takes three arguments, which are the name of the
   * class, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_AUTHZ_UNABLE_TO_INSTANTIATE_HANDLER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 533;



  /**
   * The message ID for the message that will be used if an error occurs
   * while attempting to initialize the access control handler. This
   * takes two arguments, which are the DN of the configuration entry
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIG_AUTHZ_ERROR_INITIALIZING_HANDLER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 534;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the root DN base entry from the configuration.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_ROOTDN_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 535;



  /**
   * The message ID for the message that will be used if the root DN base entry
   * does not exist in the Directory Server configuration.  This does not take
   * any arguments.
   */
  public static final int MSGID_CONFIG_ROOTDN_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 536;



  /**
   * The message ID for the message that will be used if a root DN configuration
   * entry does not contain an acceptable root DN configuration.  This takes two
   * arguments, which are the DN of the configuration entry and the reason that
   * it is not acceptable.
   */
  public static final int MSGID_CONFIG_ROOTDN_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 537;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a root DN definition from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_ROOTDN_CANNOT_CREATE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 538;



  /**
   * The message ID for the message that will be used if an entry below the
   * root DN base does not contain a valid root DN objectclass.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_ROOTDN_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 539;



  /**
   * The message ID for the message that will be used as the description for the
   * alternate bind DN configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 540;



  /**
   * The message ID for the message that will be used if a root DN configuration
   * entry defines an alternate root DN mapping that conflicts with an existing
   * mapping for another root user.  This takes three arguments, which are the
   * conflicting alternate DN, the DN of the new root user for which the mapping
   * was to be established, and the DN of the existing root user that already
   * had the mapping.
   */
  public static final int MSGID_CONFIG_ROOTDN_CONFLICTING_MAPPING =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 541;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the alternate bind DNs for the root user.  This takes two
   * arguments, which are the DN of the root user and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ROOTDN_CANNOT_PARSE_ALTERNATE_BIND_DNS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 542;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to register an alternate bind DN for a root user.  This takes three
   * arguments, which are the alternate bind DN, the DN of the root user, and a
   * message explaining the problem that occurred.
   */
  public static final int
       MSGID_CONFIG_ROOTDN_CANNOT_REGISTER_ALTERNATE_BIND_DN =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 543;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a root DN user that conflicts with another root DN already registered.
   * This takes a single argument, which is the DN of the root user being added.
   */
  public static final int MSGID_CONFIG_ROOTDN_EXISTS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 544;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the account status notification handler base entry
   * from the configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 545;



  /**
   * The message ID for the message that will be used if the account status
   * notification handler base entry does not exist in the Directory Server
   * configuration.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 546;



  /**
   * The message ID for the message that will be used if an account status
   * notification handler configuration entry does not contain an acceptable
   * configuration.  This takes two arguments, which are the DN of the
   * configuration entry and the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 547;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create an account status notification handler from a
   * configuration entry.  This takes two arguments, which are the DN of the
   * configuration entry and a message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_CANNOT_CREATE_HANDLER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 548;



  /**
   * The message ID for the message that will be used if an entry below the
   * account status notification handler base does not contain a valid handler
   * objectclass.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 549;



  /**
   * The message ID for the description of the account status notification
   * handler class name configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 550;



  /**
   * The message ID for the message that will be used if an entry below the
   * account status notification handler base does not contain a value for the
   * class name.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 551;



  /**
   * The message ID for the message that will be used if an entry below the
   * account status notification handler base contains an invalid value for the
   * class name.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 552;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server account status notification handler but the
   * associated class cannot be instantiated as a notification handler.  This
   * takes three arguments, which are the handler class name, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 553;



  /**
   * The message ID for the description of the account status notification
   * handler enabled configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 554;



  /**
   * The message ID for the message that will be used if an entry below the
   * account status notification handler base does not contain a value for the
   * enabled attribute.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 555;



  /**
   * The message ID for the message that will be used if an entry below the
   * account status notification handler base has an invalid value for the
   * enabled attribute.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 556;



  /**
   * The message ID for the message that will be used if the account status
   * notification handler class has changed and will require administrative
   * action to take effect.  This takes three arguments, which are the old class
   * name, the new class name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 557;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server account status notification handler.  This
   * takes three arguments, which are the class name for the handler class, the
   * DN of the configuration entry, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 558;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new account status notification handler with a DN that matches the DN
   * of a handler that already exists.  This takes a single argument, which is
   * the DN of the notification handler configuration entry.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 559;



  /**
   * The message ID for the message that will be used if an account status
   * notification handler entry contains an unacceptable configuration but does
   * not provide any specific details about the nature of the problem.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_ACCTNOTHANDLER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 560;


  /**
   * The message ID for the description of the server lookthrough limit
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_LOOKTHROUGH_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 561;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server lookthrough limit.  This takes two arguments,
   * which are the DN of the configuration entry and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_LOOKTHROUGH_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 562;



  /**
   * The message ID for the message that will be used an error occurs while
   * attempting to apply a set of changes on server startup.  This takes two
   * arguments, which are the path to the changes file and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_CONFIG_UNABLE_TO_APPLY_STARTUP_CHANGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 563;



  /**
   * The message ID for the message that will be used to report an error that
   * occurred while processing a startup changes file.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIG_ERROR_APPLYING_STARTUP_CHANGE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 564;



  /**
   * The message ID for the message that will be used to indicate that a problem
   * occurred while applying the startup changes.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_UNABLE_TO_APPLY_CHANGES_FILE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 565;



  /**
   * The message ID used to describe the attribute which configure the
   * file permissions mode for the database directory.
   */
  public static final int MSGID_CONFIG_DESCRIPTION_BACKEND_MODE =
      CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 566;



  /**
   * The message ID for the message that will be used if the backend directory
   * file permission mode atrribute is not a valid UNIX mode.
   */
  public static final int MSGID_CONFIG_BACKEND_MODE_INVALID =
      CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 567;



  /**
   * The message ID of an error indicating that the file permissions for the
   * database directory will result in an inaccessable database. The orginal or
   * default value will be used instead
   */
  public static final int MSGID_CONFIG_BACKEND_INSANE_MODE =
      CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_WARNING | 568;



  /**
   * The message ID for the description of the server time limit configuration
   * attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_CORE_DESCRIPTION_TIME_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 569;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server time limit.  This takes two arguments, which
   * are the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_CORE_INVALID_TIME_LIMIT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 570;



  /**
   * The message ID for the message that will be used if no default password
   * policy has been defined.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_PWPOLICY_NO_DEFAULT_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 571;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to register a backend with the Directory Server.  This takes two
   * arguments, which are the backend ID and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 572;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the configuration archive directory but no additional
   * information is available to explain the underlying problem.  This takes a
   * single argument, which is the path to the archive directory to be created.
   */
  public static final int
       MSGID_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR_NO_REASON =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 573;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the configuration archive directory.  This takes two
   * arguments, which are the path to the archive directory to be created and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 574;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write the current configuration to the configuration archive.
   * This takes two arguments, which are the path to the archive directory to be
   * created and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_FILE_CANNOT_WRITE_CONFIG_ARCHIVE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 575;


  /**
   * The message ID for the description of whether the server should
   * reject unauthenticated requests.  This does not take any arguments.
   */
  public static final int
      MSGID_CONFIG_CORE_DESCRIPTION_REJECT_UNAUTHENTICATED_REQUESTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 576;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to process the server configuration for rejecting the
   * unauthenticated operations.This takes two arguments, which are the DN of
   * the configuration entry and a string representation of the exception that
   * was caught.
   */
  public static final int
      MSGID_CONFIG_CORE_REJECT_UNAUTHENTICATED_REQUESTS_INVALID =
           CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 577;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the group implementation base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_GROUP_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 578;



  /**
   * The message ID for the message that will be used if the group
   * implementation base entry does not exist in the Directory Server
   * configuration.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_GROUP_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 579;



  /**
   * The message ID for the message that will be used if a group implementation
   * configuration entry does not contain an acceptable group implementation
   * configuration.  This takes two arguments, which are the DN of the
   * configuration entry and the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_GROUP_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 580;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a group implementation from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_GROUP_CANNOT_CREATE_IMPLEMENTATION =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 581;



  /**
   * The message ID for the message that will be used if an entry below the
   * group implementation base does not contain a valid objectclass.  This takes
   * a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_GROUP_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 582;



  /**
   * The message ID for the description of the group implementation class name
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_GROUP_DESCRIPTION_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 583;



  /**
   * The message ID for the message that will be used if an entry below the
   * group implementation base does not contain a value for the class name.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_GROUP_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 584;



  /**
   * The message ID for the message that will be used if an entry below the
   * group implementation base contains an invalid value for the class name.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_GROUP_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 585;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server group implementation but the associated class
   * cannot be instantiated as a group implementation.  This takes three
   * arguments, which are the handler class name, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_GROUP_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 586;



  /**
   * The message ID for the description of the group implementation enabled
   * configuration attribute.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_GROUP_DESCRIPTION_ENABLED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 587;



  /**
   * The message ID for the message that will be used if an entry below the
   * group implementation base does not contain a value for the enabled
   * attribute.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_CONFIG_GROUP_NO_ENABLED_ATTR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 588;



  /**
   * The message ID for the message that will be used if an entry below the
   * group implementation base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_GROUP_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 589;



  /**
   * The message ID for the message that will be used if the group
   * implementation class has changed and will require administrative action to
   * take effect.  This takes three arguments, which are the old class name, the
   * new class name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_GROUP_CLASS_ACTION_REQUIRED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 590;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server group implementation.  This takes three
   * arguments, which are the class name for the implementation class, the DN of
   * the configuration entry, and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_CONFIG_GROUP_INITIALIZATION_FAILED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 591;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new group implementation entry with a DN that matches the DN of a
   * group implementation that already exists.  This takes a single argument,
   * which is the DN of the handler configuration entry.
   */
  public static final int MSGID_CONFIG_GROUP_EXISTS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 592;



  /**
   * The message ID for the message that will be used if a group implementation
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_GROUP_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 593;



  /**
   * The message ID for the message that will be used as the description for the
   * default root privilege names configuration attribute.  This does not take
   * any arguments.
   */
  public static final int MSGID_CONFIG_ROOTDN_DESCRIPTION_ROOT_PRIVILEGE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 594;



  /**
   * The message ID for the message that will be used if the set of root
   * privileges contains an unrecognized privilege.  This takes three arguments,
   * which are the name of the attribute holding the privilege names, the DN of
   * the configuration entry, and the name of the unrecognized privilege.
   */
  public static final int MSGID_CONFIG_ROOTDN_UNRECOGNIZED_PRIVILEGE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 595;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the set of root privileges.  This takes a single
   * argument, which is a stack trace of the exception that was caught.
   */
  public static final int
       MSGID_CONFIG_ROOTDN_ERROR_DETERMINING_ROOT_PRIVILEGES =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 596;



  /**
   * The message ID for the message that will be used to indicate that the set
   * of root privileges has been updated.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_ROOTDN_UPDATED_PRIVILEGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 597;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an add operation in the server configuration but the user doesn't
   * have the necessary privileges to do so.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_FILE_ADD_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 598;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a delete operation in the server configuration but the user doesn't
   * have the necessary privileges to do so.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_FILE_DELETE_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 599;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify operation in the server configuration but the user doesn't
   * have the necessary privileges to do so.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_FILE_MODIFY_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 600;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify DN operation in the server configuration but the user
   * doesn't have the necessary privileges to do so.  This does not take any
   * arguments.
   */
  public static final int MSGID_CONFIG_FILE_MODDN_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 601;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a search operation in the server configuration but the user doesn't
   * have the necessary privileges to do so.  This does not take any arguments.
   */
  public static final int MSGID_CONFIG_FILE_SEARCH_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 602;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * modify the set of default root privileges but the user doesn't have the
   * necessary privileges to do so.  This does not take any arguments.
   */
  public static final int
       MSGID_CONFIG_FILE_MODIFY_PRIVS_INSUFFICIENT_PRIVILEGES =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 603;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the certificate mapper base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 604;



  /**
   * The message ID for the message that will be used if the certificate mapper
   * base entry does not exist in the Directory Server configuration.  This does
   * not take any arguments.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 605;



  /**
   * The message ID for the message that will be used if a certificate mapper
   * configuration entry does not contain an acceptable mapper configuration.
   * This takes two arguments, which are the DN of the configuration entry and
   * the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 606;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a certificate mapper from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_CANNOT_CREATE_MAPPER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 607;



  /**
   * The message ID for the message that will be used if an entry below the
   * certificate mapper base does not contain a valid objectclass.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 608;



  /**
   * The message ID for the message that will be used if an entry below the
   * certificate mapper base does not contain a value for the class name.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 609;



  /**
   * The message ID for the message that will be used if an entry below the
   * certificate mapper base contains an invalid value for the class name.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 610;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server certificate mapper but the associated class
   * cannot be instantiated as a certificate mapper.  This takes three
   * arguments, which are the mapper class name, the DN of the configuration
   * entry, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 611;



  /**
   * The message ID for the message that will be used if an entry below the
   * certificate mapper base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 612;



  /**
   * The message ID for the message that will be used if the certificate mapper
   * class has changed and will require administrative action to take effect.
   * This takes three arguments, which are the old class name, the new class
   * name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 613;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server certificate maper.  This takes three
   * arguments, which are the class name for the mapper class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 614;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new certificate mapper with a DN that matches the DN of a mapper that
   * already exists.  This takes a single argument, which is the DN of the
   * mapper configuration entry.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 615;



  /**
   * The message ID for the message that will be used if a certificate mapper
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_CERTMAPPER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 616;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the key manager provider base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 617;



  /**
   * The message ID for the message that will be used if the key manager
   * provider base entry does not exist in the Directory Server configuration.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 618;



  /**
   * The message ID for the message that will be used if a key manager provider
   * configuration entry does not contain an acceptable provider configuration.
   * This takes two arguments, which are the DN of the configuration entry and
   * the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 619;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a key manager provider from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_CANNOT_CREATE_PROVIDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 620;



  /**
   * The message ID for the message that will be used if an entry below the
   * key manager provider base does not contain a valid objectclass.  This takes
   * a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 621;



  /**
   * The message ID for the message that will be used if an entry below the
   * key manager provider base does not contain a value for the class name.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 622;



  /**
   * The message ID for the message that will be used if an entry below the
   * key manager provider base contains an invalid value for the class name.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 623;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server key manager provider but the associated class
   * cannot be instantiated as a provider.  This takes three arguments, which
   * are the provider class name, the DN of the configuration entry, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 624;



  /**
   * The message ID for the message that will be used if an entry below the
   * key manager provider base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 625;



  /**
   * The message ID for the message that will be used if the key manager
   * provider class has changed and will require administrative action to take
   * effect.  This takes three arguments, which are the old class name, the new
   * class name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 626;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server key manager provider.  This takes three
   * arguments, which are the class name for the provider class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 627;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new key manager provider with a DN that matches the DN of a provider
   * that already exists.  This takes a single argument, which is the DN of the
   * provider configuration entry.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 628;



  /**
   * The message ID for the message that will be used if a key manager provider
   * entry contains an unacceptable configuration but does not provide any
   * specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_KEYMANAGER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 629;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the trust manager provider base entry from the
   * configuration.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_CANNOT_GET_BASE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 630;



  /**
   * The message ID for the message that will be used if the trust manager
   * provider base entry does not exist in the Directory Server configuration.
   * This does not take any arguments.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_BASE_DOES_NOT_EXIST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 631;



  /**
   * The message ID for the message that will be used if a trust manager
   * provider configuration entry does not contain an acceptable provider
   * configuration. This takes two arguments, which are the DN of the
   * configuration entry and the reason that it is not acceptable.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_ENTRY_UNACCEPTABLE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 632;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a trust manager provider from a configuration entry.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * message that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_CANNOT_CREATE_PROVIDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 633;



  /**
   * The message ID for the message that will be used if an entry below the
   * trust manager provider base does not contain a valid objectclass.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_INVALID_OBJECTCLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 634;



  /**
   * The message ID for the message that will be used if an entry below the
   * trust manager provider base does not contain a value for the class name.
   * This takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_NO_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 635;



  /**
   * The message ID for the message that will be used if an entry below the
   * trust manager provider base contains an invalid value for the class name.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_INVALID_CLASS_NAME =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 636;



  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server trust manager provider but the associated class
   * cannot be instantiated as a provider.  This takes three arguments, which
   * are the provider class name, the DN of the configuration entry, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_INVALID_CLASS =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 637;



  /**
   * The message ID for the message that will be used if an entry below the
   * trust manager provider base has an invalid value for the enabled attribute.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_INVALID_ENABLED_VALUE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 638;



  /**
   * The message ID for the message that will be used if the trust manager
   * provider class has changed and will require administrative action to take
   * effect.  This takes three arguments, which are the old class name, the new
   * class name, and the DN of the associated configuration entry.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_CLASS_ACTION_REQUIRED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 639;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing a Directory Server trust manager provider.  This takes three
   * arguments, which are the class name for the provider class, the DN of the
   * configuration entry, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_INITIALIZATION_FAILED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 640;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new trust manager provider with a DN that matches the DN of a
   * provider that already exists.  This takes a single argument, which is the
   * DN of the provider configuration entry.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_EXISTS =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 641;



  /**
   * The message ID for the message that will be used if a trust manager
   * provider entry contains an unacceptable configuration but does not provide
   * any specific details about the nature of the problem.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_CONFIG_TRUSTMANAGER_UNACCEPTABLE_CONFIG =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 642;



  /**
   * The message ID for the message that will be used if it is not possible to
   * retrieve a JMX attribute.  This takes three arguments, which are the name
   * of the attribute to retrieve, the DN of the associated configuration entry,
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIG_JMX_CANNOT_GET_ATTRIBUTE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 643;



  /**
   * The message ID for the message that will be used if it is not possible to
   * set a JMX attribute because there is no available JMX connection.  This
   * takes two arguments, which are the name of the attribute and the DN of the
   * associated configuration entry.
   */
  public static final int MSGID_CONFIG_JMX_SET_ATTR_NO_CONNECTION =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 644;



  /**
   * The message ID for the message that will be used if a configuration add,
   * delete, or change listener or a configurable component return a result of
   * {@code null} instead of a valid config change result.  This takes three
   * arguments, which are the class name of the object, the name of the method
   * that was invoked, and the DN of the target entry.
   */
  public static final int MSGID_CONFIG_CHANGE_NO_RESULT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 645;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the configuration as a result of an added, deleted,
   * or modified configuration entry.  This takes six arguments, which are the
   * class name of the object that generated the error, the name of the method
   * that was invoked, the DN of the target entry, the result code generated,
   * whether administrative action is required to apply the change, and any
   * messages generated.
   */
  public static final int MSGID_CONFIG_CHANGE_RESULT_ERROR =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 646;



  /**
   * The message ID for the message that will be used if a configuration change
   * requires some kind of administrative action before it will take effect.
   * This takes four arguments, which are the class name of the object that
   * indicated the action was required, the name of the method that was invoked,
   * the DN of the target entry, and any messages generated.
   */
  public static final int MSGID_CONFIG_CHANGE_RESULT_ACTION_REQUIRED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 647;



  /**
   * The message ID for the message that will be used if a configuration change
   * was successful and no administrative action is required, but there were
   * messages generated.  This takes four arguments, which are the name of the
   * object that performed the processing, the name of the method that was
   * invoked, the DN of the target entry, and the messages generated.
   */
  public static final int MSGID_CONFIG_CHANGE_RESULT_MESSAGES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_INFORMATIONAL | 648;



  /**
   * The message ID for the message that will be used if a virtual attribute
   * definition has an invalid search filter.  This takes three arguments, which
   * are the filter string, the configuration entry DN, and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_CONFIG_VATTR_INVALID_SEARCH_FILTER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 649;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load and/or initialize a class as a virtual attribute provider.
   * This takes three arguments, which are the class name, the configuration
   * entry DN, and string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_VATTR_INITIALIZATION_FAILED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 650;



  /**
   * The message ID for the message that will be used if the configured
   * attribute type is single-valued, but the virtual attribute provider may
   * generate multiple values.  This takes three arguments, which are the DN of
   * the configuration entry, the name or OID of the attribute type, and the
   * name of the virtual attribute provider class.
   */
  public static final int MSGID_CONFIG_VATTR_SV_TYPE_WITH_MV_PROVIDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 651;



  /**
   * The message ID for the message that will be used if the configured
   * attribute type is single-valued, but the conflict behavior is to merge the
   * real and virtual values.  This takes two arguments, which are the DN of
   * the configuration entry and the name or OID of the attribute type.
   */
  public static final int MSGID_CONFIG_VATTR_SV_TYPE_WITH_MERGE_VALUES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 652;



  /**
   * The message ID for the message that will be used if a an attempt is made to
   * modify an entry in the config backend in a manner that will change its
   * structural object class.
   */
  public static final int
       MSGID_CONFIG_FILE_MODIFY_STRUCTURAL_CHANGE_NOT_ALLOWED =
            CATEGORY_MASK_CONFIG | SEVERITY_MASK_MILD_ERROR | 653;



  /**
   * The message ID for the message that will be used an error occurs while
   * attempting to calculate a digest of the server configuration.  This takes
   * two arguments, which are the path to the file the server is trying to
   * digest, and a string representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_CANNOT_CALCULATE_DIGEST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_FATAL_ERROR | 654;



  /**
   * The message ID for the message that will be used if the server detects that
   * the configuration has been manually edited while the server is online, but
   * that the manual edits have been copied off into another file before the
   * configuration was updated by another change.  This takes two arguments,
   * which are the path to the live configuration file and the path to the file
   * containing the manual edits.
   */
  public static final int MSGID_CONFIG_MANUAL_CHANGES_DETECTED =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 655;



  /**
   * The message ID for the message that will be used if the server detects that
   * the configuration may have been manually edited while the server is online,
   * but a problem occurred that prevented the manual changes from being copied
   * before the configuration was overwritten.  This takes two arguments, which
   * are the path to the live configuration file and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_CONFIG_MANUAL_CHANGES_LOST =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 656;



   /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server rotation policy but the associated class cannot
   * be instantiated as a rotation policy.  This takes three arguments, which
   * are the class name, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_ROTATION_POLICY_INVALID_CLASS =
      CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 657;


  /**
   * The message ID for the message that will be used if a configuration entry
   * defines a Directory Server retention policy but the associated class cannot
   * be instantiated as a retention policy.  This takes three arguments, which
   * are the class name, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_CONFIG_RETENTION_POLICY_INVALID_CLASS =
      CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 658;


  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a rotation policy from a configuration entry.  This takes
   * two arguments, which are the DN of the configuration entry and a message
   * that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_ROTATION_POLICY_CANNOT_CREATE_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 659;


  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a retention policy from a configuration entry.  This takes
   * two arguments, which are the DN of the configuration entry and a message
   * that explains the problem that occurred.
   */
  public static final int MSGID_CONFIG_RETENTION_POLICY_CANNOT_CREATE_POLICY =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 660;


  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a text writer for a text log publisher.  This takes
   * two arguments, the DN of the onfiguration entry and a message that explains
   * the problem that occurred.
   */
  public static final int MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_ERROR | 661;



  /**
   * The message ID for the message that will be used if a schema configuration
   * file is found to have multiple entries.  This takes two arguments, which
   * are the name of the schema file and the path to the schema directory.
   */
  public static final int MSGID_CONFIG_SCHEMA_MULTIPLE_ENTRIES_IN_FILE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 662;



  /**
   * The message ID for the message that will be used if a schema configuration
   * file is found to have unparseable data after the first entry.  This takes
   * three arguments, which are the name of the schema file, the path to the
   * schema directory, and a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIG_SCHEMA_UNPARSEABLE_EXTRA_DATA_IN_FILE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 663;



  /**
   * The message ID for the message that will be used if a plugin order
   * definition contains an empty element (i.e., two consecutive commas).  This
   * takes a single argument, which is the name of the plugin type.
   */
  public static final int MSGID_CONFIG_PLUGIN_EMPTY_ELEMENT_IN_ORDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 664;



  /**
   * The message ID for the message that will be used if a plugin order
   * definition contains multiple wildcard characters.  This takes a single
   * argument, which is the name of the plugin type.
   */
  public static final int MSGID_CONFIG_PLUGIN_MULTIPLE_WILDCARDS_IN_ORDER =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 665;



  /**
   * The message ID for the message that will be used if a plugin is listed
   * multiple times in the plugin order.  This takes two arguments, which are
   * the plugin type and the plugin name.
   */
  public static final int MSGID_CONFIG_PLUGIN_LISTED_MULTIPLE_TIMES =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 666;



  /**
   * The message ID for the message that will be used if a plugin order does
   * not contain a wildcard.  This takes a single argument, which is the name of
   * the plugin type.
   */
  public static final int MSGID_CONFIG_PLUGIN_ORDER_NO_WILDCARD =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 667;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * delete an attribute syntax that is in use by an attribute type.  This takes
   * two arguments, which is the name of the attribute syntax and the name or
   * OID of the attribute type that is using that syntax.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_DELETE_SYNTAX_IN_USE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 668;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * disable an attribute syntax that is in use by an attribute type.  This
   * takes two arguments, which is the name of the attribute syntax and the name
   * or  OID of the attribute type that is using that syntax.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_DISABLE_SYNTAX_IN_USE =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 669;



  /**
   * The message ID for the message that will be used if a matching rule cannot
   * be removed because it is in use by an existing attribute type.  This takes
   * two arguments, which are the name of the matching rule and the name or OID
   * of the attribute type.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 670;



  /**
   * The message ID for the message that will be used if a matching rule cannot
   * be removed because it is in use by an existing matching rule use.  This
   * takes two arguments, which are the name of the matching rule and the name
   * of the matching rule use.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_MRU =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 671;



  /**
   * The message ID for the message that will be used if a matching rule cannot
   * be disabled because it is in use by an existing attribute type.  This takes
   * two arguments, which are the name of the matching rule and the name or OID
   * of the attribute type.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 672;



  /**
   * The message ID for the message that will be used if a matching rule cannot
   * be disabled because it is in use by an existing matching rule use.  This
   * takes two arguments, which are the name of the matching rule and the name
   * of the matching rule use.
   */
  public static final int MSGID_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_MRU =
       CATEGORY_MASK_CONFIG | SEVERITY_MASK_SEVERE_WARNING | 673;




  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_CONFIG_ATTR_IS_REQUIRED,
                    "Configuration attribute %s is required to have at least " +
                    "one value but the resulted operation would have removed " +
                    "all values");
    registerMessage(MSGID_CONFIG_ATTR_REJECTED_VALUE,
                    "Provided value %s for configuration attribute %s was " +
                    "rejected.  The reason provided was:  %s");
    registerMessage(MSGID_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED,
                    "Configuration attribute %s is single-valued, but " +
                    "multiple values were provided");
    registerMessage(MSGID_CONFIG_ATTR_ADD_VALUES_IS_SINGLE_VALUED,
                    "Configuration attribute %s is single-valued, but adding " +
                    "the provided value(s) would have given it multiple " +
                    "values");
    registerMessage(MSGID_CONFIG_ATTR_ADD_VALUES_ALREADY_EXISTS,
                    "Configuration attribute %s already contains a value %s");
    registerMessage(MSGID_CONFIG_ATTR_NO_SUCH_VALUE,
                    "Cannot remove value %s from configuration attribute %s " +
                    "because the specified value does not exist");
    registerMessage(MSGID_CONFIG_ATTR_OPTIONS_NOT_ALLOWED,
                    "Invalid configuration attribute %s detected in entry " +
                    "%s:  the only attribute option allowed in the Directory " +
                    "Server configuration is \"" + OPTION_PENDING_VALUES +
                    "\" to indicate the set of pending values");
    registerMessage(MSGID_CONFIG_ATTR_MULTIPLE_PENDING_VALUE_SETS,
                    "Configuration attribute %s appears to contain multiple " +
                    "pending value sets");
    registerMessage(MSGID_CONFIG_ATTR_MULTIPLE_ACTIVE_VALUE_SETS,
                    "Configuration attribute %s appears to contain multiple " +
                    "active value sets");
    registerMessage(MSGID_CONFIG_ATTR_NO_ACTIVE_VALUE_SET,
                    "Configuration attribute %s does not contain an active " +
                    "value set");
    registerMessage(MSGID_CONFIG_CANNOT_GET_CONFIG_ENTRY,
                    "An error occurred while attempting to retrieve " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_SET_ATTRIBUTE,
                    "The value of configuration attribute %s has been set to " +
                    "%s in configuration entry %s");
    registerMessage(MSGID_CONFIG_CHANGE_NOT_SUCCESSFUL,
                    "The attempt to update configuration entry %s was not " +
                    "successful and one or more problems were encountered:  " +
                    "%s");


    registerMessage(MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE,
                    "Unable to set the value for Boolean configuration " +
                    "attribute %s because the provided value %s was not " +
                    "either 'true' or 'false'");


    registerMessage(MSGID_CONFIG_ATTR_NO_INT_VALUE,
                    "Unable to retrieve the value for configuration " +
                    "attribute %s as an integer because that attribute does " +
                    "not have any values");
    registerMessage(MSGID_CONFIG_ATTR_MULTIPLE_INT_VALUES,
                    "Unable to retrieve the value for configuration " +
                    "attribute %s as an integer because that attribute has " +
                    "multiple values");
    registerMessage(MSGID_CONFIG_ATTR_VALUE_OUT_OF_INT_RANGE,
                    "Unable to retrieve the value for configuration " +
                    "attribute %s as a Java int because the value is outside " +
                    "the allowable range for an int");
    registerMessage(MSGID_CONFIG_ATTR_INVALID_INT_VALUE,
                    "Unable to set the value for integer configuration " +
                    "attribute %s because the provided value %s cannot be " +
                    "interpreted as an integer value:  %s");
    registerMessage(MSGID_CONFIG_ATTR_INT_BELOW_LOWER_BOUND,
                    "Unable to set the value for configuration attribute %s " +
                    "because the provided value %d is less than the lowest " +
                    "allowed value of %d");
    registerMessage(MSGID_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND,
                    "Unable to set the value for configuration attribute %s " +
                    "because the provided value %d is greater than the " +
                    "largest allowed value of %d");
    registerMessage(MSGID_CONFIG_ATTR_INT_COULD_NOT_PARSE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as an integer value:  %s");
    registerMessage(MSGID_CONFIG_ATTR_INT_INVALID_TYPE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as an integer value because the element was of an " +
                    "invalid type (%s)");
    registerMessage(MSGID_CONFIG_ATTR_INT_INVALID_ARRAY_TYPE,
                    "Unable to parse value for configuration attribute %s " +
                    "as a set of integer values because the array contained " +
                    "elements of an invalid type (%s)");


    registerMessage(MSGID_CONFIG_ATTR_NO_STRING_VALUE,
                    "Unable to retrieve the value for configuration " +
                    "attribute %s as a string because that attribute does " +
                    "not have any values");
    registerMessage(MSGID_CONFIG_ATTR_MULTIPLE_STRING_VALUES,
                    "Unable to retrieve the value for configuration " +
                    "attribute %s as a string because that attribute has " +
                    "multiple values");
    registerMessage(MSGID_CONFIG_ATTR_EMPTY_STRING_VALUE,
                    "An empty value string was provided for configuration " +
                    "attribute %s");
    registerMessage(MSGID_CONFIG_ATTR_INVALID_STRING_VALUE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as a string value:  %s");
    registerMessage(MSGID_CONFIG_ATTR_STRING_INVALID_TYPE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as a string value because the element was of an invalid " +
                    "type (%s)");
    registerMessage(MSGID_CONFIG_ATTR_STRING_INVALID_ARRAY_TYPE,
                    "Unable to parse value for configuration attribute %s " +
                    "as a set of string values because the array contained " +
                    "elements of an invalid type (%s)");


    registerMessage(MSGID_CONFIG_ATTR_VALUE_NOT_ALLOWED,
                    "The value %s is not included in the list of acceptable " +
                    "values for configuration attribute %s");
    registerMessage(MSGID_CONFIG_ATTR_READ_ONLY,
                    "Configuration attribute %s is read-only and its values " +
                    "may not be altered");


    registerMessage(MSGID_CONFIG_ATTR_INVALID_UNIT,
                    "'%s' is not a valid unit for configuration attribute %s");
    registerMessage(MSGID_CONFIG_ATTR_NO_UNIT_DELIMITER,
                    "Cannot decode %s as an integer value and a unit for " +
                    "configuration attribute %s because no value/unit " +
                    "delimiter could be found");
    registerMessage(MSGID_CONFIG_ATTR_COULD_NOT_PARSE_INT_COMPONENT,
                    "Could not decode the integer portion of value %s for " +
                    "configuration attribute %s:  %s");
    registerMessage(MSGID_CONFIG_ATTR_INVALID_VALUE_WITH_UNIT,
                    "The provided value %s for integer with unit attribute " +
                    "%s is not allowed:  %s");
    registerMessage(MSGID_CONFIG_ATTR_INT_WITH_UNIT_INVALID_TYPE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as an integer with unit value because the element was " +
                    "of an invalid type (%s)");


    registerMessage(MSGID_CONFIG_ENTRY_CONFLICTING_CHILD,
                    "Unable to add configuration entry %s as a child of " +
                    "configuration entry %s because a child entry was " +
                    "already found with that DN");
    registerMessage(MSGID_CONFIG_ENTRY_NO_SUCH_CHILD,
                    "Unable to remove entry %s as a child of configuration " +
                    "entry %s because that entry did not have a child with " +
                    "the specified DN");
    registerMessage(MSGID_CONFIG_ENTRY_CANNOT_REMOVE_NONLEAF,
                    "Unable to remove entry %s as a child of configuration " +
                    "entry %s because that entry had children of its own and " +
                    "non-leaf entries may not be removed");
    registerMessage(MSGID_CONFIG_ENTRY_CANNOT_REMOVE_CHILD,
                    "An unexpected error occurred while attempting to remove " +
                    "entry %s as a child of configuration entry %s:  %s");


    registerMessage(MSGID_CONFIG_FILE_DOES_NOT_EXIST,
                    "The specified configuration file %s does not exist or " +
                    "is not readable");
    registerMessage(MSGID_CONFIG_FILE_CANNOT_VERIFY_EXISTENCE,
                    "An unexpected error occurred while attempting to " +
                    "determine whether configuration file %s exists:  %s");
    registerMessage(MSGID_CONFIG_CANNOT_CALCULATE_DIGEST,
                    "An error occurred while attempting to calculate a SHA-1 " +
                    "digest of file %s:  %s");
    registerMessage(MSGID_CONFIG_UNABLE_TO_APPLY_STARTUP_CHANGES,
                    "An error occurred while attempting to apply the changes " +
                    "contained in file %s to the server configuration at " +
                    "startup:  %s");
    registerMessage(MSGID_CONFIG_FILE_CANNOT_OPEN_FOR_READ,
                    "An error occurred while attempting to open the " +
                    "configuration file %s for reading:  %s");
    registerMessage(MSGID_CONFIG_FILE_READ_ERROR,
                    "An error occurred while attempting to read the contents " +
                    "of configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_INVALID_LDIF_ENTRY,
                    "An error occurred at or near line %d while trying to " +
                    "parse the configuration from LDIF file %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_EMPTY,
                    "The specified configuration file %s does not appear to " +
                    "contain any configuration entries");
    registerMessage(MSGID_CONFIG_FILE_INVALID_BASE_DN,
                    "The first entry read from LDIF configuration file %s " +
                    "had a DN of \"%s\" rather than the expected \"%s\" " +
                    "which should be used as the Directory Server " +
                    "configuration root");
    registerMessage(MSGID_CONFIG_FILE_GENERIC_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "process the Directory Server configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_DUPLICATE_ENTRY,
                    "Configuration entry %s starting at or near line %s in " +
                    "the LDIF configuration file %s has the same DN as " +
                    "another entry already read from that file");
    registerMessage(MSGID_CONFIG_FILE_NO_PARENT,
                    "Configuration entry %s starting at or near line %d in " +
                    "the configuration LDIF file %s does not appear to have " +
                    "a parent entry (expected parent DN was %s)");
    registerMessage(MSGID_CONFIG_FILE_UNKNOWN_PARENT,
                    "The Directory Server was unable to determine the parent " +
                    "DN for configuration entry %s starting at or near line " +
                    "%d in the configuration LDIF file %s");
    registerMessage(MSGID_CONFIG_CANNOT_DETERMINE_SERVER_ROOT,
                    "Unable to determine the Directory Server instance root " +
                    "from either an environment variable or based on the " +
                    "location of the configuration file.  Please set an " +
                    "environment variable named %s with a value containing " +
                    "the absolute path to the server installation root");
    registerMessage(MSGID_CONFIG_LDIF_WRITE_ERROR,
                    "An unexpected error occurred while trying to export " +
                    "the Directory Server configuration to LDIF:  %s");
    registerMessage(MSGID_CONFIG_FILE_WRITE_ERROR,
                    "An unexpected error occurred while trying to write " +
                    "configuration entry %s to LDIF:  %s");
    registerMessage(MSGID_CONFIG_FILE_CLOSE_ERROR,
                    "An unexpected error occurred while trying to close " +
                    "the LDIF writer:  %s");
    registerMessage(MSGID_CONFIG_FILE_UNWILLING_TO_IMPORT,
                    "The Directory Server configuration may not be altered " +
                    "by importing a new configuration from LDIF");
    registerMessage(MSGID_CONFIG_CANNOT_REGISTER_AS_PRIVATE_SUFFIX,
                    "An unexpected error occurred while trying to register " +
                    "the configuration handler base DN \"%s\" as a private " +
                    "suffix with the Directory Server:  %s");
    registerMessage(MSGID_CONFIG_ERROR_APPLYING_STARTUP_CHANGE,
                    "Unable to apply a change at server startup:  %s");
    registerMessage(MSGID_CONFIG_UNABLE_TO_APPLY_CHANGES_FILE,
                    "One or more errors occurred while applying changes on " +
                    "server startup");
    registerMessage(MSGID_CONFIG_FILE_ADD_ALREADY_EXISTS,
                    "Entry %s cannot be added to the Directory Server " +
                    "configuration because another configuration entry " +
                    "already exists with that DN");
    registerMessage(MSGID_CONFIG_FILE_ADD_NO_PARENT_DN,
                    "Entry %s cannot be added to the Directory Server " +
                    "configuration because that DN does not have a parent");
    registerMessage(MSGID_CONFIG_FILE_ADD_NO_PARENT,
                    "Entry %s cannot be added to the Directory Server " +
                    "configuration because its parent entry %s does not " +
                    "exist");
    registerMessage(MSGID_CONFIG_FILE_ADD_REJECTED_BY_LISTENER,
                    "The Directory Server is unwilling to add configuration " +
                    "entry %s because one of the add listeners registered " +
                    "with the parent entry %s rejected this change with " +
                    "the message:  %s");
    registerMessage(MSGID_CONFIG_FILE_ADD_FAILED,
                    "An unexpected error occurred while attempting to add " +
                    "configuration entry %s as a child of entry %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_DELETE_NO_SUCH_ENTRY,
                    "Entry %s cannot be removed from the Directory Server " +
                    "configuration because the specified entry does not " +
                    "exist");
    registerMessage(MSGID_CONFIG_FILE_DELETE_HAS_CHILDREN,
                    "Entry %s cannot be removed from the Directory Server " +
                    "configuration because the specified entry has one or " +
                    "more subordinate entries");
    registerMessage(MSGID_CONFIG_FILE_DELETE_NO_PARENT,
                    "Entry %s cannot be removed from the Directory Server " +
                    "configuration because the entry does not have a parent " +
                    "and removing the configuration root entry is not " +
                    "allowed");
    registerMessage(MSGID_CONFIG_FILE_DELETE_REJECTED,
                    "Entry %s cannot be removed from the Directory Server " +
                    "configuration because one of the delete listeners " +
                    "registered with the parent entry %s rejected this " +
                    "change with the message:  %s");
    registerMessage(MSGID_CONFIG_FILE_DELETE_FAILED,
                    "An unexpected error occurred while attempting to remove " +
                    "configuration entry %s as a child of entry %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_MODIFY_NO_SUCH_ENTRY,
                    "Entry %s cannot be modified because the specified entry " +
                    "does not exist");
    registerMessage(MSGID_CONFIG_FILE_MODIFY_STRUCTURAL_CHANGE_NOT_ALLOWED,
                    "Configuration entry %s cannot be modified because the " +
                    "change would alter its structural object class");
    registerMessage(MSGID_CONFIG_FILE_MODIFY_REJECTED_BY_CHANGE_LISTENER,
                    "Entry %s cannot be modified because one of the " +
                    "configuration change listeners registered for that " +
                    "entry rejected the change:  %s");
    registerMessage(MSGID_CONFIG_FILE_MODIFY_REJECTED_BY_COMPONENT,
                    "Entry %s cannot be modified because one of the " +
                    "configurable components registered for that entry " +
                    "rejected the change:  %s");
    registerMessage(MSGID_CONFIG_FILE_SEARCH_NO_SUCH_BASE,
                    "The search operation cannot be processed because base " +
                    "entry %s does not exist");
    registerMessage(MSGID_CONFIG_FILE_SEARCH_INVALID_SCOPE,
                    "The search operation cannot be processed because the " +
                    "specified search scope %s is invalid");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_CREATE_TEMP_ARCHIVE,
                    "An error occurred while attempting to create a " +
                    "temporary file %s to hold the archived versions of " +
                    "previous Directory Server configurations as a result of " +
                    "a configuration change:  %s");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_COPY_EXISTING_ARCHIVE,
                    "An error occurred while attempting to copy the " +
                    "archived configurations from %s to temporary file %s:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_COPY_CURRENT_CONFIG,
                    "An error occurred while attempting to update the " +
                    "archived configurations in file %s to include the " +
                    "running configuration from file %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_RENAME_TEMP_ARCHIVE,
                    "The attempt to rename the archived configuration data " +
                    "from %s to %s failed, but the underlying reason for the " +
                    "failure could not be determined");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_REPLACE_ARCHIVE,
                    "The updated archive containing previous Directory " +
                    "Server configurations could not be renamed from %s to " +
                    "%s:  %s");
    registerMessage(MSGID_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR_NO_REASON,
                    "An error occurred while trying to create the " +
                    "configuration archive directory %s");
    registerMessage(MSGID_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR,
                    "An error occurred while trying to create the " +
                    "configuration archive directory %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_CANNOT_WRITE_CONFIG_ARCHIVE,
                    "An error occurred while trying to write the current " +
                    "configuration to the configuration archive:  %s");
    registerMessage(MSGID_CONFIG_MANUAL_CHANGES_DETECTED,
                    "The Directory Server has detected that one or more " +
                    "external changes have been made to the configuration " +
                    "file %s while the server was online, but another change " +
                    "has caused the server configuration to be overwritten.  " +
                    "The manual changes have not been applied, but they have " +
                    "been preserved in file %s");
    registerMessage(MSGID_CONFIG_MANUAL_CHANGES_LOST,
                    "The Directory Server encountered an error while " +
                    "attempting to determine whether the configuration file " +
                    "%s has been externally edited with the server online, " +
                    "and/or trying to preserve such changes:  %s.  Any " +
                    "manual changes made to that file may have been lost");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_EXPORT_NEW_CONFIG,
                    "An error occurred while attempting to export the new " +
                    "Directory Server configuration to file %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_WRITE_CANNOT_RENAME_NEW_CONFIG,
                    "An error occurred while attempting to rename the new " +
                    "Directory Server configuration from file %s to %s:  %s");
    registerMessage(MSGID_CONFIG_FILE_MODDN_NOT_ALLOWED,
                    "Modify DN operations are not allowed in the Directory " +
                    "Server configuration");
    registerMessage(MSGID_CONFIG_FILE_HEADER,
                    "This file contains the primary Directory Server " +
                    "configuration.  It must not be directly edited while " +
                    "the server is online.  The server configuration should " +
                    "only be managed using the administration utilities " +
                    "provided with the Directory Server");
    registerMessage(MSGID_CONFIG_FILE_ADD_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to perform add " +
                    "operations in the Directory Server configuration");
    registerMessage(MSGID_CONFIG_FILE_DELETE_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to perform delete " +
                    "operations in the Directory Server configuration");
    registerMessage(MSGID_CONFIG_FILE_MODIFY_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to perform modify " +
                    "operations in the Directory Server configuration");
    registerMessage(MSGID_CONFIG_FILE_MODIFY_PRIVS_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to change the set " +
                    "of default root privileges");
    registerMessage(MSGID_CONFIG_FILE_MODDN_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to perform modify " +
                    "DN operations in the Directory Server configuration");
    registerMessage(MSGID_CONFIG_FILE_SEARCH_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to perform search " +
                    "operations in the Directory Server configuration");


    registerMessage(MSGID_CONFIG_LOGGER_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "base logger entry " + DN_LOGGER_BASE + " from the " +
                    "Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_BASE_DOES_NOT_EXIST,
                    "The logger configuration base " + DN_LOGGER_BASE +
                    " does not exist in the Directory Server configuration.  " +
                    "Logging facilities will not be available until this " +
                    "entry is created and the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_LOGGER_NO_ACTIVE_ACCESS_LOGGERS,
                    "There are no active access loggers defined in the " +
                    "Directory Server configuration.  No access logging will " +
                    "be performed");
    registerMessage(MSGID_CONFIG_LOGGER_NO_ACTIVE_ERROR_LOGGERS,
                    "There are no active error loggers defined in the " +
                    "Directory Server configuration.  No error logging will " +
                    "be performed");
    registerMessage(MSGID_CONFIG_LOGGER_NO_ACTIVE_DEBUG_LOGGERS,
                    "There are no active debug loggers defined in the " +
                    "Directory Server configuration.  No debug logging will " +
                    "be performed");
    registerMessage(MSGID_CONFIG_LOGGER_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid logger " +
                    "configuration:  %s.  It will be ignored");
    registerMessage(MSGID_CONFIG_LOGGER_CANNOT_CREATE_LOGGER,
                    "An error occurred while attempting to create a " +
                    "Directory Server logger from the information in " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain a valid " +
                    "objectclass for a Directory Server access, error, or " +
                    "debug logger definition");
    registerMessage(MSGID_CONFIG_LOGGER_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_LOGGER_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated logger");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_LOGGER_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS,
                    "Class %s specified in attribute " + ATTR_LOGGER_CLASS +
                    " of configuration entry %s cannot be instantiated as " +
                    "a Directory Server access logger:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS,
                    "Class %s specified in attribute " + ATTR_LOGGER_CLASS +
                    " of configuration entry %s cannot be instantiated as " +
                    "a Directory Server error logger:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS,
                    "Class %s specified in attribute " + ATTR_LOGGER_CLASS +
                    " of configuration entry %s cannot be instantiated as " +
                    "a Directory Server debug logger:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_LOGGER_ENABLED +
                    " which indicates whether the logger should be enabled " +
                    "for use in the Directory Server");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_LOGGER_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server logger.  If this is altered while " +
                    "the associated logger is enabled, then that logger must " +
                    "be disabled and re-enabled for the change to take " +
                    "effect");
    registerMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server logger should " +
                    "be enabled.  Changes to this attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_LOGGER_NO_FILE_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_LOGGER_FILE +
                    " which specifies the log file name for " +
                    "the associated logger");
    registerMessage(MSGID_CONFIG_LOGGER_CLASS_ACTION_REQUIRED,
                    "The requested change in the logger class name from %s " +
                    "to %s in configuration entry %s cannot be dynamically " +
                    "applied.  This change will not take effect until the " +
                    "logger is disabled and re-enabled or the Directory " +
                    "Server is restarted");
    registerMessage(MSGID_CONFIG_LOGGER_EXISTS,
                    "Unable to add a new logger entry with DN %s because " +
                    "there is already a logger registered with that DN");
    registerMessage(MSGID_CONFIG_LOGGER_ACCESS_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as an access logger as defined in " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_ERROR_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as an error logger as defined in " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_DEBUG_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a debug logger as defined in " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_LOGGER_UNACCEPTABLE_CONFIG,
                    "The configuration for the logger defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_UNKNOWN_UNACCEPTABLE_REASON,
                    "Unknown unacceptable reason");


    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_ADD_MISSING_RDN_ATTRS,
                    "Indicates whether the Directory Server should " +
                    "automatically add missing RDN attributes to an entry " +
                    "when it is added.  By default, entries added that do " +
                    "not contain the RDN attribute values in their attribute " +
                    "lists will be rejected because they are in violation " +
                    "of the LDAP specification.  Changes to this " +
                    "configuration attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_ADD_MISSING_RDN_ATTRS,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " +
                    ATTR_ADD_MISSING_RDN_ATTRS + " (it should be a Boolean " +
                    "value of true or false):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_ALLOW_ATTR_EXCEPTIONS,
                    "Indicates whether to allow some flexibility in the " +
                    "characters that may be used in attribute names.  By " +
                    "default, attribute names may only contain ASCII " +
                    "alphabetic letters, numeric digits, and dashes, and " +
                    "they must begin with a letter.  If attribute name " +
                    "exceptions are enabled, then the underscore character " +
                    "will also be allowed and the attribute name may also " +
                    "start with a digit.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_ALLOW_EXCEPTIONS,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " +
                    ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS + " (it should be a " +
                    "Boolean value of true or false):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_SERVER_ERROR_RESULT_CODE,
                    "Specifies the result code that should be used for " +
                    "responses in which the operation fails because of an " +
                    "internal server error.  The value should be the " +
                    "integer equivalent of the corresponding LDAP result " +
                    "code.  Changes to this configuration attribute will " +
                    "take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_SERVER_ERROR_RESULT_CODE,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " +
                    ATTR_SERVER_ERROR_RESULT_CODE + " (it should be an " +
                    "integer value greater than or equal to 1):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_INVALID_SYNTAX_BEHAVIOR,
                    "Specifies the behavior that the Directory Server should " +
                    "exhibit if it discovers an attribute whose value does " +
                    "not conform to the syntax for that attribute.  " +
                    "Acceptable values for this attribute are \"reject\" to " +
                    "reject the invalid value, \"warn\" to accept the " +
                    "invalid value but log a warning message, or \"accept\" " +
                    "to accept the invalid value with no warning.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_ENFORCE_STRICT_SYNTAX,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_INVALID_SYNTAX_BEHAVIOR +
                    " (it should be one of \"accept\", \"reject\", or " +
                    "\"warn\" ):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_MAX_ALLOWED_CONNECTIONS,
                    "The maximum number of client connections that may be " +
                    "established to the Directory Server at any given time.  " +
                    "a value that is less than or equal to zero indicates " +
                    "that there should be no limit.  Changes to this " +
                    "configuration attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_MAX_ALLOWED_CONNECTIONS,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_MAX_ALLOWED_CONNS +
                    " (it should be an integer value)");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_STRUCTURAL_CLASS_BEHAVIOR,
                    "Specifies the behavior that the Directory Server should " +
                    "exhibit if it discovers an entry that does not have " +
                    "exactly one structural objectclass.  Acceptable values " +
                    "are \"reject\" to reject the entry, \"warn\" to accept " +
                    "the entry but log a warning message, or \"accept\" to " +
                    "accept the invalid entry with no warning.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_STRUCTURAL_CLASS_BEHAVIOR,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " +
                    ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR +
                    " (it should be one of \"accept\", \"reject\", or " +
                    "\"warn\" ):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_CHECK_SCHEMA,
                    "Indicates whether the Directory Server should perform " +
                    "schema checking for update operations to ensure that " +
                    "entries are valid according to the server's schema " +
                    "configuration (e.g., all required attributes are " +
                    "included and no prohibited attributes are present).  " +
                    "Disabling schema checking is generally not recommended " +
                    "because it may allow invalid entries to be included in " +
                    "the server.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_CHECK_SCHEMA,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_CHECK_SCHEMA +
                    " (it should be a Boolean value of true or false):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_NOTIFY_ABANDONED_OPERATIONS,
                    "Indicates whether the Directory Server should send a " +
                    "response to operations that have been abandoned to " +
                    "the client to know that the server has completed " +
                    "processing on them.  The LDAP specification prohibits " +
                    "sending a response in such cases, but some clients may " +
                    "not behave properly if they are waiting on a response " +
                    "for an operation when there will not be one because it " +
                    "has been abandoned.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_PROXY_MAPPER_DN,
                    "Specifies the DN of the configuration entry for the " +
                    "identity mapper that the Directory Server should use in " +
                    "conjunction with the proxied authorization V2 control.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_NOTIFY_ABANDONED_OPERATIONS,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_NOTIFY_ABANDONED_OPS +
                    " (it should be a Boolean value of true or false):  %s");
    registerMessage(MSGID_CONFIG_CORE_INVALID_PROXY_MAPPER_DN,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_PROXY_MAPPER_DN +
                    " (it should be the DN of a valid identity mapper " +
                    "configuration entry):  %s");
    registerMessage(MSGID_CONFIG_CORE_NO_PROXY_MAPPER_FOR_DN,
                    "The proxied authorization identity mapper DN %s " +
                    "specified in configuration entry %s does not refer to a " +
                    "valid identity mapper configuration entry");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_SIZE_LIMIT,
                    "Specifies the default maximum number of entries that " +
                    "should be returned to a client when processing a search " +
                    "operation.  This may be overridden on a per-user basis " +
                    "by including the " + OP_ATTR_USER_SIZE_LIMIT +
                    " operational attribute in the user's entry.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_SIZE_LIMIT,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_SIZE_LIMIT +
                    " (It should be a positive integer value specifying " +
                    "the size limit to use, or a value of 0 or -1 to " +
                    "indicate that no limit should be enforced):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_TIME_LIMIT,
                    "Specifies the default maximum length of time that " +
                    "should be allowed when processing a search operation.  " +
                    "This may be overridden on a per-user basis by including " +
                    "the " + OP_ATTR_USER_TIME_LIMIT + " operational " +
                    "attribute in the user's entry.  Changes to this " +
                    "configuration attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_TIME_LIMIT,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_TIME_LIMIT +
                    " (it should be an integer value followed by a space " +
                    "and a time unit of seconds, minutes, hours, days, or " +
                    "weeks):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_WRITABILITY_MODE,
                    "Specifies the writability mode for the Directory " +
                    "Server.  The value may be one of \"enabled\", " +
                    "\"disabled\", or \"internal-only\".  Changes to this " +
                    "configuration attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_WRITABILITY_MODE,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_WRITABILITY_MODE +
                    " (the value should be one of \"enabled\", \"disabled\", " +
                    "or \"internal-only\"):  %s");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_BIND_WITH_DN_REQUIRES_PW,
                    "Indicates whether simple binds that contain a DN will " +
                    "also be required to contain a password.  If this is " +
                    "disabled, then binds with no password will always be " +
                    "treated as anonymous, even if they contain a bind DN, " +
                    "which can create a security hole for some kinds of " +
                    "applications.  With this option enabled, then any " +
                    "simple bind request that contains a DN but no password " +
                    "will be rejected rather than defaulting to anonymous " +
                    "authentication.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_DEFAULT_PWPOLICY_DN,
                    "Specifies the DN of the configuration entry that " +
                    "defines the default password policy for the Directory " +
                    "Server, which will be applied for all users for which a " +
                    "custom password policy is not defined.  This entry must " +
                    "exist and must specify a valid password policy " +
                    "configuration.  Changes to this configuration attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_BIND_WITH_DN_REQUIRES_PW,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_BIND_WITH_DN_REQUIRES_PW +
                    " (the value should be either \"TRUE\" or \"FALSE\"):  " +
                    "%s");
    registerMessage(MSGID_CONFIG_CORE_NO_DEFAULT_PWPOLICY,
                    "No default password policy was configured for the " +
                    "Directory Server.  This must be specified by the " +
                    ATTR_DEFAULT_PWPOLICY_DN + " attribute in configuration " +
                    "entry %s");
    registerMessage(MSGID_CONFIG_CORE_INVALID_DEFAULT_PWPOLICY_DN,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_DEFAULT_PWPOLICY_DN +
                    " (the value should a DN specifying the default " +
                    "password policy configuration entry):  %s");
    registerMessage(MSGID_CONFIG_CORE_NO_SUCH_PWPOLICY,
                    "The value %s for configuration attribute " +
                    ATTR_DEFAULT_PWPOLICY_DN + " does not refer to a valid " +
                    "password policy configuration entry");


    registerMessage(MSGID_CONFIG_JMX_ATTR_NO_VALUE,
                    "Configuration entry %s does not contain a value for " +
                    "attribute %s");
    registerMessage(MSGID_CONFIG_JMX_ATTR_NO_ATTR,
                    "Configuration entry %s does not contain attribute %s " +
                    "(or that attribute exists but is not accessible using " +
                    "JMX)");
    registerMessage(MSGID_CONFIG_JMX_CANNOT_GET_CONFIG_ENTRY,
                    "Unable to retrieve configuration entry %s for access " +
                    "through JMX:  %s");
    registerMessage(MSGID_CONFIG_JMX_ATTR_INVALID_VALUE,
                    "Attempted update to attribute %s of configuration entry " +
                    "%s over JMX would have resulted in an invalid value:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_JMX_UNACCEPTABLE_CONFIG,
                    "Update to configuration entry %s over JMX would have " +
                    "resulted in an invalid configuration:  %s");
    registerMessage(MSGID_CONFIG_JMX_NO_METHOD,
                    "There is no method %s for any invokable component " +
                    "registered with configuration entry %s");
    registerMessage(MSGID_CONFIG_JMX_CANNOT_REGISTER_MBEAN,
                    "The Directory Server could not register a JMX MBean for " +
                    "the component associated with configuration entry %s:  " +
                    "%s ");
    registerMessage(MSGID_CONFIG_JMX_CANNOT_GET_ATTRIBUTE,
                    "Unable to retrieve JMX attribute %s associated with " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_JMX_SET_ATTR_NO_CONNECTION,
                    "Unable to set the value of JMX attribute %s associated " +
                    "with configuration entry %s because no JMX connection " +
                    "is avaialble");


    registerMessage(MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_NUM_THREADS,
                    "Specifies the number of worker threads that should " +
                    "be used to process requests, which controls the number " +
                    "of operations that the server may process " +
                    "concurrently.  The optimal value depends on the type of " +
                    "system on which the server is running and the workload " +
                    "it needs to handle, and may best be determined by " +
                    "performance testing.  Changes to this attribute will " +
                    "take effect immediately");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_DESCRIPTION_MAX_CAPACITY,
                    "Specifies the maximum number of pending requests that " +
                    "may be held in the work queue at any one time while all " +
                    "worker threads are busy processing other operations.  " +
                    "If this limit is reached, then any new requests will be " +
                    "rejected.  A value of 0 indicates that there is no " +
                    "limit.  Changes to this attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_NUM_WORKER_THREADS,
                    "An error occurred while attempting to retrieve the " +
                    "value of the " + ATTR_NUM_WORKER_THREADS + " attribute " +
                    "from the %s entry, which is used to specify the number " +
                    "of worker threads to service the work queue:  %s.  The " +
                    "Directory Server will use the default value of " +
                    DEFAULT_NUM_WORKER_THREADS + "");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_CANNOT_DETERMINE_QUEUE_CAPACITY,
                    "An error occurred while attempting to retrieve the " +
                    "value of the " + ATTR_MAX_WORK_QUEUE_CAPACITY +
                    " attribute from the %s entry, which is used to specify " +
                    "the maximum number of pending operations that may be " +
                    "held in the work queue:  %s.  The Directory Server will " +
                    "use the default value of " +
                    DEFAULT_MAX_WORK_QUEUE_CAPACITY + "");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_NUM_THREADS_INVALID_VALUE,
                    "The value of configuration attribute " +
                    ATTR_NUM_WORKER_THREADS + " in configuration entry %s " +
                    "has an invalid value (%d).  This attribute requires " +
                    "an integer value greater than zero");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_CAPACITY_INVALID_VALUE,
                    "The value of configuration attribute " +
                    ATTR_MAX_WORK_QUEUE_CAPACITY + " in configuration entry " +
                    "%s has an invalid value (%d).  This attribute requires " +
                    "an integer value greater than or equal to zero");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_CREATED_THREADS,
                    "%d additional worker threads have been created to bring " +
                    "the total number of available threads to %d");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_DESTROYING_THREADS,
                    "%d worker threads will terminate as soon as it is " +
                    "convenient to do so (it may take a couple of seconds " +
                    "for the threads to actually exit) to bring the total " +
                    "number of available threads to %d");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_NEW_CAPACITY,
                    "The work queue capacity has been updated to use a new "+
                    "value of %d");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_TOO_MANY_FAILURES,
                    "Worker thread \"%s\" has experienced too many repeated " +
                    "failures while attempting to retrieve the next " +
                    "operation from the work queue (%d failures experienced, " +
                    "maximum of %d failures allowed).  This worker thread " +
                    "will be destroyed");
    registerMessage(MSGID_CONFIG_WORK_QUEUE_CANNOT_CREATE_MONITOR,
                    "A problem occurred while trying to create and start an " +
                    "instance of class %s to use as a monitor provider for " +
                    "the Directory Server work queue:  %s.  No monitor " +
                    "information will be available for the work queue");

   registerMessage(MSGID_CONFIG_DESCRIPTION_BACKEND_DIRECTORY,
                   "The name of the directory in which backend database " +
                   "files are stored");
   registerMessage(MSGID_CONFIG_BACKEND_NO_DIRECTORY,
                   "Configuration entry %s does not contain a valid value " +
                   "for configuration attribute " +
                   ATTR_BACKEND_DIRECTORY);


    registerMessage(MSGID_CONFIG_ATTR_DN_NULL,
                    "A null value was provided for DN configuration " +
                    "attribute %s");
    registerMessage(MSGID_CONFIG_ATTR_DN_CANNOT_PARSE,
                    "An error occurred while trying to parse value \"%s\" of " +
                    "attribute %s as a DN:  %s");
    registerMessage(MSGID_CONFIG_ATTR_INVALID_DN_VALUE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as a DN:  %s");
    registerMessage(MSGID_CONFIG_ATTR_DN_INVALID_TYPE,
                    "Unable to parse value %s for configuration attribute %s " +
                    "as a DN because the element was of an invalid type (%s)");
    registerMessage(MSGID_CONFIG_ATTR_DN_INVALID_ARRAY_TYPE,
                    "Unable to parse value for configuration attribute %s " +
                    "as a set of DN values because the array contained " +
                    "elements of an invalid type (%s)");


    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE,
                    "An error occurred while trying to retrieve " +
                    "configuration entry " + DN_BACKEND_BASE + " in order to " +
                    "initialize the Directory Server backends:  %s");
    registerMessage(MSGID_CONFIG_BACKEND_BASE_DOES_NOT_EXIST,
                    "The entry " + DN_BACKEND_BASE +  " does not appear to " +
                    "exist in the Directory Server configuration.  This is a " +
                    "required entry");
    registerMessage(MSGID_CONFIG_BACKEND_ENTRY_DOES_NOT_HAVE_BACKEND_CONFIG,
                    "Configuration entry %s exists below the backend " +
                    "configuration root of " + DN_BACKEND_BASE + " but does " +
                    "not have objectclass " + OC_BACKEND + " that is " +
                    "required for a Directory Server backend.  This " +
                    "configuration entry will be ignored");
    registerMessage(MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY,
                    "An unexpected error occurred while interacting with " +
                    "backend configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_ENABLED,
                    "Indicates whether this backend should be enabled for " +
                    "use in the Directory Server.  This may be altered while " +
                    "the Directory Server is online, but if a backend is " +
                    "disabled, then it will not be available for use");
    registerMessage(MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID,
                    "Specifies the unique identifier for this backend.  " +
                    "Changes to this configuration attribute will not take " +
                    "effect until the backend is disabled and re-enabled or " +
                    "the server is restarted");
    registerMessage(MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_WRITABILITY,
                    "Specifies the writability mode for this backend.  The " +
                    "value may be \"enabled\" if all write operations will " +
                    "be allowed, \"disabled\" if all write operations will " +
                    "be rejected, or \"internal-only\" if only internal " +
                    "write operations and synchronization updates will be " +
                    "allowed.  Changes to this configuration attribute will " +
                    "take effect immediately");
    registerMessage(MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that should provide the core logic for this backend " +
                    "implementation.  Changes to this configuration " +
                    "attribute will not take effect until the backend is "+
                    "disabled and re-enabled or the server is restarted");
    registerMessage(MSGID_CONFIG_BACKEND_NO_ENABLED_ATTR,
                    "Backend configuration entry %s does not contain " +
                    "attribute " + ATTR_BACKEND_ENABLED + ", which is used " +
                    "to indicate whether the backend should be enabled or " +
                    "disabled.  Without this attribute, it will default to " +
                    "being disabled");
    registerMessage(MSGID_CONFIG_BACKEND_DISABLED,
                    "The backend defined in configuration entry %s is " +
                    "marked as disabled and therefore will not be used");
    registerMessage(MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE,
                    "An unexpected error occurred while attempting to " +
                    "determine whether the backend associated with " +
                    "configuration entry %s should be enabled or disabled:  " +
                    "%s.  It will be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_NO_BACKEND_ID,
                    "Backend configuration entry %s does not contain " +
                    "attribute " + ATTR_BACKEND_ID + ", which is used " +
                    "to provide a unique identifier for the backend.  The " +
                    "backend will be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID,
                    "The backend defined in configuration entry %s has a " +
                    "backend ID of %s that conflicts with the backend ID for " +
                    "another backend in the server.  The backend will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BACKEND_ID,
                    "An unexpected error occurred while attempting to " +
                    "determine the backend ID for the backend defined in " +
                    "configuration entry %s:  %s.  The backend will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_BACKEND_NO_WRITABILITY_MODE,
                    "The backend defined in configuration entry %s does not " +
                    "have a value for configuration attribute " +
                    ATTR_BACKEND_WRITABILITY_MODE + " which indicates the " +
                    "writability mode for that backend.  The backend will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_BACKEND_INVALID_WRITABILITY_MODE,
                    "The backend defined in configuration entry %s has an " +
                    "invalid writability mode of %s.  The backend will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_WRITABILITY,
                    "An unexpected error occurred while attempting to " +
                    "determine the writability mode for the backend defined " +
                    "in configuration entry %s:  %s.  The backend will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_BACKEND_NO_CLASS_ATTR,
                    "Backend configuration entry %s does not contain " +
                    "attribute " + ATTR_BACKEND_CLASS + ", which is used to " +
                    "specify the name of the class used to provide the " +
                    "backend implementation.  The backend associated with " +
                    "this configuration entry will be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_GET_CLASS,
                    "An unexpected error occurred while trying to determine " +
                    "the name of the Java class that contains the " +
                    "implementation for backend %s:  %s.  This backend will " +
                    "be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE,
                    "The Directory Server was unable to load class %s and " +
                    "use it to create a backend instance as defined in " +
                    "configuration entry %s.  The error that occurred was:  " +
                    "%s.  This backend will be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK,
                    "The Directory Server was unable to acquire a shared " +
                    "lock for backend %s:  %s.  This generally means that " +
                    "the backend is in use by a process that requires an " +
                    "exclusive lock (e.g., importing from LDIF or restoring " +
                    "a backup).  This backend will be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE,
                    "An error occurred while trying to initialize a backend " +
                    "loaded from class %s with the information in " +
                    "configuration entry %s:  %s.  This backend will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK,
                    "An error occurred while attempting to release a shared " +
                    "lock for backend %s:  %s.  This may interfere with " +
                    "operations that require exclusive access, including " +
                    "LDIF import and restoring a backup");
    registerMessage(MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND,
                    "An error occurred while attempting to register backend " +
                    "%s with the Directory Server:  %s");
    registerMessage(MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND,
                    "The class %s specified in configuration entry %s does " +
                    "not contain a valid Directory Server backend " +
                    "implementation");
    registerMessage(MSGID_CONFIG_BACKEND_ACTION_REQUIRED_TO_CHANGE_CLASS,
                    "The requested change to configuration entry %s would " +
                    "cause the class for the associated backend to change " +
                    "from %s to %s.  This change will not take effect until " +
                    "the backend is disabled and re-enabled, or until the " +
                    "Directory Server is restarted");
    registerMessage(
         MSGID_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES,
         "The backend defined in configuration entry %s has one or more " +
         "subordinate backends.  A backend may not be removed if it has " +
         "subordinate backends");
    registerMessage(MSGID_CONFIG_BACKEND_UNACCEPTABLE_CONFIG,
                    "The configuration for the backend defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");
    registerMessage(MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS,
                    "Specifies the set of base DNs that should be used for " +
                    "this backend.  It is up to the backend implementation " +
                    "as to whether changes to this attribute will " +
                    "automatically take effect");
    registerMessage(MSGID_CONFIG_BACKEND_NO_BASE_DNS,
                    "Backend configuration entry %s does not contain " +
                    "attribute " + ATTR_BACKEND_BASE_DN + ", which is used " +
                    "to specify the set of base DNs for the backend.  This " +
                    "is a required attribute, and therefore the backend will " +
                    "be disabled");
    registerMessage(MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_BASE_DNS,
                    "An unexpected error occurred while attempting to " +
                    "determine the set of base DNs associated with the " +
                    "backend defined in configuration entry %s:  %s.  This " +
                    "backend will be disabled");


    registerMessage(MSGID_CONFIG_MONITOR_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "base monitor entry " + DN_MONITOR_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_MONITOR_BASE_DOES_NOT_EXIST,
                    "The monitor configuration base " + DN_MONITOR_CONFIG_BASE +
                    " does not exist in the Directory Server configuration.  " +
                    "Only limited monitoring information will be available");
    registerMessage(MSGID_CONFIG_MONITOR_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid monitor " +
                    "provider configuration:  %s.  It will be ignored");
    registerMessage(MSGID_CONFIG_MONITOR_CANNOT_CREATE_MONITOR,
                    "An error occurred while attempting to create a " +
                    "Directory Server monitor provider from the information " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_MONITOR_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_MONITOR_PROVIDER + " objectclass, which is required " +
                    "for monitor provider definitions");
    registerMessage(MSGID_CONFIG_MONITOR_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server monitor provider.  If this is " +
                    "altered while the associated monitor is enabled, then " +
                    "that monitor must be disabled and re-enabled for the " +
                    "change to take effect");
    registerMessage(MSGID_CONFIG_MONITOR_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_MONITOR_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated monitor provider");
    registerMessage(MSGID_CONFIG_MONITOR_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_MONITOR_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_MONITOR_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid monitor provider implementation:  %s");
    registerMessage(MSGID_CONFIG_MONITOR_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server monitor " +
                    "provider should be enabled.  Changes to this attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_CONFIG_MONITOR_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_MONITOR_ENABLED +
                    " which indicates whether the monitor provider should be " +
                    "enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_MONITOR_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_MONITOR_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_MONITOR_CLASS_ACTION_REQUIRED,
                    "The requested change in the monitor class name from %s " +
                    "to %s in configuration entry %s cannot be dynamically " +
                    "applied.  This change will not take effect until the " +
                    "monitor provider is disabled and re-enabled or the " +
                    "Directory Server is restarted");
    registerMessage(MSGID_CONFIG_MONITOR_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a monitor provider as defined " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_MONITOR_EXISTS,
                    "Unable to add a new monitor provider entry with DN %s " +
                    "because there is already a monitor provider registered " +
                    "with that DN");
    registerMessage(MSGID_CONFIG_MONITOR_UNACCEPTABLE_CONFIG,
                    "The configuration for the monitor provider defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CONFIG_BASE,
                    "An error occurred while trying to retrieve " +
                    "configuration entry " + DN_CONNHANDLER_BASE +
                    " in order to initialize the Directory Server backends:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_CONNHANDLER_BASE_DOES_NOT_EXIST,
                    "The entry " + DN_CONNHANDLER_BASE +  " does not appear " +
                    "to exist in the Directory Server configuration.  This " +
                    "is a required entry");
    registerMessage(
         MSGID_CONFIG_CONNHANDLER_ENTRY_DOES_NOT_HAVE_CONNHANDLER_CONFIG,
         "Configuration entry %s exists below the connection handler " +
         "configuration root of " + DN_CONNHANDLER_BASE + " but does not " +
         "have objectclass " + OC_CONNECTION_HANDLER + " that is required " +
         "for a Directory Server connection handler.  This configuration " +
         "entry will be ignored");
    registerMessage(
         MSGID_CONFIG_CONNHANDLER_ERROR_INTERACTING_WITH_CONNHANDLER_ENTRY,
         "An unexpected error occurred while interacting with connection " +
         "handler configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_ENABLED,
                    "Indicates whether this connection handler should be " +
                    "enabled for use in the Directory Server.  This may be " +
                    "altered while the Directory Server is online, but if a " +
                    "connection handler is disabled, then it will not be " +
                    "available for use");
    registerMessage(MSGID_CONFIG_CONNHANDLER_ATTR_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that should provide the core logic for this connection " +
                    "handler implementation.  Changes to this configuration " +
                    "attribute will not take effect until the connection " +
                    "handler is disabled and re-enabled or the server is " +
                    "restarted");
    registerMessage(MSGID_CONFIG_CONNHANDLER_NO_ENABLED_ATTR,
                    "Connection handler configuration entry %s does not " +
                    "contain attribute " + ATTR_CONNECTION_HANDLER_ENABLED +
                    ", which is used to indicate whether the connection " +
                    "handler should be enabled or disabled.  Without this " +
                    "attribute, it will default to being disabled");
    registerMessage(MSGID_CONFIG_CONNHANDLER_DISABLED,
                    "The connection handler defined in configuration entry " +
                    "%s is marked as disabled and therefore will not be used");
    registerMessage(MSGID_CONFIG_CONNHANDLER_UNABLE_TO_DETERMINE_ENABLED_STATE,
                    "An unexpected error occurred while attempting to " +
                    "determine whether the connection handler associated " +
                    "with configuration entry %s should be enabled or " +
                    "disabled:  %s.  It will be disabled");
    registerMessage(MSGID_CONFIG_CONNHANDLER_NO_CLASS_ATTR,
                    "Connection handler configuration entry %s does not " +
                    "contain attribute " + ATTR_CONNECTION_HANDLER_CLASS +
                    ", which is used to specify the name of the class used " +
                    "to provide the connection handler implementation.  The " +
                    "connection handler associated with this configuration " +
                    "entry will be disabled");
    registerMessage(MSGID_CONFIG_CONNHANDLER_CANNOT_GET_CLASS,
                    "An unexpected error occurred while trying to determine " +
                    "the name of the Java class that contains the " +
                    "implementation for connection handler %s:  %s.  This " +
                    "connection handler will be disabled");
    registerMessage(MSGID_CONFIG_CONNHANDLER_CANNOT_INSTANTIATE,
                    "The Directory Server was unable to load class %s and " +
                    "use it to create a connection handler instance as " +
                    "defined in configuration entry %s.  The error that " +
                    "occurred was:  %s.  This connection handler will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_CONNHANDLER_CANNOT_INITIALIZE,
                    "An error occurred while trying to initialize a " +
                    "connection handler loaded from class %s with the " +
                    "information in configuration entry %s:  %s.  This " +
                    "connection handler will be disabled");
    registerMessage(MSGID_CONFIG_CONNHANDLER_UNACCEPTABLE_CONFIG,
                    "The configuration for the connection handler defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");
    registerMessage(MSGID_CONFIG_CONNHANDLER_CLASS_NOT_CONNHANDLER,
                    "The class %s specified in configuration entry %s does " +
                    "not contain a valid Directory Server connection handler " +
                    "implementation");


    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_GET_MR_BASE,
                    "An error occurred while trying to retrieve " +
                    "configuration entry " + DN_MATCHING_RULE_CONFIG_BASE +
                    " in order to initialize the Directory Server matching " +
                    "rules:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_BASE_DOES_NOT_EXIST,
                    "The entry " + DN_MATCHING_RULE_CONFIG_BASE +
                    " does not appear to exist in the Directory Server " +
                    "configuration.  This is a required entry");
    registerMessage(MSGID_CONFIG_SCHEMA_NO_MATCHING_RULES,
                    "No child entries were found below the entry " +
                    DN_MATCHING_RULE_CONFIG_BASE + " to define the matching " +
                    "rules for use in the Directory Server.  This is an " +
                    "error, because the Directory Server must have matching " +
                    "rules defined to function properly");
    registerMessage(MSGID_CONFIG_SCHEMA_ENTRY_DOES_NOT_HAVE_MR_CONFIG,
                    "Configuration entry %s exists below the matching rule " +
                    "configuration root of " + DN_MATCHING_RULE_CONFIG_BASE +
                    " but does not have objectclass " + OC_MATCHING_RULE +
                    " that is required for a Directory Server matching " +
                    "rule.  This configuration entry will be ignored");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_ATTR_DESCRIPTION_ENABLED,
                    "Indicates whether this matching rule should be enabled " +
                    "for use in the Directory Server.  This may be altered " +
                    "while the Directory Server is online, but if a matching " +
                    "rule is disabled, after it has been used for one or " +
                    "more attributes then matching may no longer function " +
                    "as expected for those attributes");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_NO_ENABLED_ATTR,
                    "Matching rule configuration entry %s does not contain " +
                    "attribute " + ATTR_MATCHING_RULE_ENABLED + ", which is " +
                    "used to indicate whether the matching rule should be " +
                    "enabled or disabled.  Without this attribute, it will " +
                    "default to being disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_DISABLED,
                    "The matching rule defined in configuration entry " +
                    "%s is marked as disabled and therefore will not be " +
                    "used.  If it has been used in the past for one or more " +
                    "attributes, then matching may no longer function for " +
                    "values of those attributes");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_UNABLE_TO_DETERMINE_ENABLED_STATE,
                    "An unexpected error occurred while attempting to " +
                    "determine whether the matching rule associated with " +
                    "configuration entry %s should be enabled or disabled:  " +
                    "%s.  It will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_ATTR_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that should provide the core logic for this matching " +
                    "rule implementation.  Changes to this configuration " +
                    "attribute will not take effect until the matching rule " +
                    "is disabled and re-enabled or the server is " +
                    "restarted.  Also, changes to the matching rule class " +
                    "for matching rules that have already been used for one " +
                    "or more attributes may cause unexpected results when " +
                    "performing matching for those attributes");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_NO_CLASS_ATTR,
                    "Matching rule configuration entry %s does not contain " +
                    "attribute " + ATTR_MATCHING_RULE_CLASS + ", which is " +
                    "used to specify the name of the class used to provide " +
                    "the matching rule implementation.  The matching rule " +
                    "associated with this configuration entry will be " +
                    "disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_CANNOT_GET_CLASS,
                    "An unexpected error occurred while trying to determine " +
                    "the name of the Java class that contains the " +
                    "implementation for matching rule %s:  %s.  This " +
                    "matching rule will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_CANNOT_INSTANTIATE,
                    "The Directory Server was unable to load class %s and " +
                    "use it to create a matching rule instance as defined in " +
                    "configuration entry %s.  The error that occurred was:  " +
                    "%s.  This matching rule will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_CANNOT_INITIALIZE,
                    "An error occurred while trying to initialize a matching " +
                    "rule loaded from class %s with the information in " +
                    "configuration entry %s:  %s.  This matching rule will " +
                    "be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_MR_CONFLICTING_MR,
                    "The matching rule defined in configuration entry %s " +
                    "conflicts with another matching rule defined in the " +
                    "server configuration:  %s.  This matching rule will not " +
                    "be used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_AT,
                    "Matching rule %s cannot be deleted from the server " +
                    "because it is in use by attribute type %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_DELETE_MR_IN_USE_BY_MRU,
                    "Matching rule %s cannot be deleted from the server " +
                    "because it is in use by matching rule use %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_AT,
                    "Matching rule %s cannot be disabled because it is in " +
                    "use by attribute type %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_DISABLE_MR_IN_USE_BY_MRU,
                    "Matching rule %s cannot be disabled because it is in " +
                    "use by matching rule use %s");


    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_GET_SYNTAX_BASE,
                    "An error occurred while trying to retrieve " +
                    "configuration entry " + DN_SYNTAX_CONFIG_BASE +
                    " in order to initialize the Directory Server attribute " +
                    "syntaxes:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_BASE_DOES_NOT_EXIST,
                    "The entry " + DN_SYNTAX_CONFIG_BASE +
                    " does not appear to exist in the Directory Server " +
                    "configuration.  This is a required entry");
    registerMessage(MSGID_CONFIG_SCHEMA_NO_SYNTAXES,
                    "No child entries were found below the entry " +
                    DN_SYNTAX_CONFIG_BASE + " to define the attribute " +
                    "syntaxes for use in the Directory Server.  This is an " +
                    "error, because the Directory Server must have syntaxes " +
                    "defined to function properly");
    registerMessage(MSGID_CONFIG_SCHEMA_ENTRY_DOES_NOT_HAVE_SYNTAX_CONFIG,
                    "Configuration entry %s exists below the attribute " +
                    "syntax configuration root of " + DN_SYNTAX_CONFIG_BASE +
                    " but does not have objectclass " + OC_ATTRIBUTE_SYNTAX +
                    " that is required for a Directory Server attribute " +
                    "syntax.  This configuration entry will be ignored");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_ATTR_DESCRIPTION_ENABLED,
                    "Indicates whether this attribute syntax should be " +
                    "enabled for use in the Directory Server.  This may be " +
                    "altered while the Directory Server is online, but if a " +
                    "syntax is disabled, after it has been used for one or " +
                    "more attributes then matching may no longer function " +
                    "as expected for those attributes");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_NO_ENABLED_ATTR,
                    "Attribute syntax configuration entry %s does not " +
                    "contain attribute " + ATTR_SYNTAX_ENABLED + ", which is " +
                    "used to indicate whether the syntax should be enabled " +
                    "or disabled.  Without this attribute, it will default " +
                    "to being disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_DISABLED,
                    "The attribute syntax defined in configuration entry " +
                    "%s is marked as disabled and therefore will not be " +
                    "used.  If it has been used in the past for one or more " +
                    "attributes, then matching may no longer function for " +
                    "values of those attributes");
    registerMessage(
         MSGID_CONFIG_SCHEMA_SYNTAX_UNABLE_TO_DETERMINE_ENABLED_STATE,
         "An unexpected error occurred while attempting to determine whether " +
         "the attribute syntax associated with configuration entry %s should " +
         "be enabled or disabled:  %s.  It will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_ATTR_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that should provide the core logic for this attribute " +
                    "syntax implementation.  Changes to this configuration " +
                    "attribute will not take effect until the syntax is " +
                    "disabled and re-enabled or the server is restarted.  " +
                    "Also, changes to the syntax class for attribute " +
                    "syntaxes that have already been used for one or more " +
                    "attributes may cause unexpected results when performing " +
                    "matching for those attributes");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_NO_CLASS_ATTR,
                    "Matching rule configuration entry %s does not contain " +
                    "attribute " + ATTR_SYNTAX_CLASS + ", which is used to " +
                    "specify the name of the class used to provide the " +
                    "attribute syntax implementation.  The syntax associated " +
                    "with this configuration entry will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_GET_CLASS,
                    "An unexpected error occurred while trying to determine " +
                    "the name of the Java class that contains the " +
                    "implementation for attribute syntax %s:  %s.  This " +
                    "syntax will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_INSTANTIATE,
                    "The Directory Server was unable to load class %s and " +
                    "use it to create an attribute syntax instance as " +
                    "defined in configuration entry %s.  The error that " +
                    "occurred was:  %s.  This syntax will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_CANNOT_INITIALIZE,
                    "An error occurred while trying to initialize an " +
                    "attribute syntax loaded from class %s with the " +
                    "information in configuration entry %s:  %s.  This " +
                    "syntax will be disabled");
    registerMessage(MSGID_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX,
                    "The attribute syntax defined in configuration entry %s " +
                    "conflicts with another syntax defined in the server " +
                    "configuration:  %s.  This attribute syntax will not be " +
                    "used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_DELETE_SYNTAX_IN_USE,
                    "Attribute syntax %s cannot be deleted from the server " +
                    "because it is in use by attribute type %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_DISABLE_SYNTAX_IN_USE,
                    "Attribute syntax %s cannot be disabled because it is in " +
                    "use by attribute type %s");


    registerMessage(MSGID_CONFIG_SCHEMA_NO_SCHEMA_DIR,
                    "Unable to read the Directory Server schema definitions " +
                    "because the schema directory %s does not exist");
    registerMessage(MSGID_CONFIG_SCHEMA_DIR_NOT_DIRECTORY,
                    "Unable to read the Directory Server schema definitions " +
                    "because the schema directory %s exists but is not a " +
                    "directory");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_LIST_FILES,
                    "Unable to read the Directory Server schema definitions " +
                    "from directory %s because an unexpected error occurred " +
                    "while trying to list the files in that directory:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_OPEN_FILE,
                    "Schema configuration file %s in directory %s cannot be " +
                    "parsed because an unexpected error occurred while " +
                    "trying to open the file for reading:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_READ_LDIF_ENTRY,
                    "Schema configuration file %s in directory %s cannot be " +
                    "parsed because an unexpected error occurred while " +
                    "trying to read its contents as an LDIF entry:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_MULTIPLE_ENTRIES_IN_FILE,
                    "Schema configuration file %s in directory %s contains " +
                    "more than one entry.  Only the first entry will be " +
                    "examined, and the additional entries will be ignored");
    registerMessage(MSGID_CONFIG_SCHEMA_UNPARSEABLE_EXTRA_DATA_IN_FILE,
                    "Schema configuration file %s in directory %s contains " +
                    "additional data after the schema entry that cannot be " +
                    "parsed by the LDIF reader:  %s.  The first entry will " +
                    "be processed, but the remaining data will be ignored");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_PARSE_ATTR_TYPE,
                    "Unable to parse an attribute type definition from " +
                    "schema configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CONFLICTING_ATTR_TYPE,
                    "An attribute type read from schema configuration file " +
                    "%s conflicts with another attribute type already read " +
                    "into the schema:  %s.  The later attribute type " +
                    "definition will be used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_PARSE_OC,
                    "Unable to parse an objectclass definition from schema " +
                    "configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CONFLICTING_OC,
                    "An objectclass read from schema configuration file %s " +
                    "conflicts with another objectclass already read into " +
                    "the schema:  %s.  The later objectclass definition will " +
                    "be used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_PARSE_NAME_FORM,
                    "Unable to parse a name form definition from schema " +
                    "configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CONFLICTING_NAME_FORM,
                    "A name form read from schema configuration file %s " +
                    "conflicts with another name form already read into " +
                    "the schema:  %s.  The later name form definition will " +
                    "be used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DCR,
                    "Unable to parse a DIT content rule definition from " +
                    "schema configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CONFLICTING_DCR,
                    "A DIT content rule read from schema configuration file " +
                    "%s conflicts with another DIT content rule already read " +
                    "into the schema:  %s.  The later DIT content rule " +
                    "definition will be used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_PARSE_DSR,
                    "Unable to parse a DIT structure rule definition from " +
                    "schema configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CONFLICTING_DSR,
                    "A DIT structure rule read from schema configuration " +
                    "file %s conflicts with another DIT structure rule " +
                    "already read into the schema:  %s.  The later DIT " +
                    "structure rule definition will be used");
    registerMessage(MSGID_CONFIG_SCHEMA_CANNOT_PARSE_MRU,
                    "Unable to parse a matching rule use definition from " +
                    "schema configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_SCHEMA_CONFLICTING_MRU,
                    "A matching rule use read from schema configuration " +
                    "file %s conflicts with another matching rule use " +
                    "already read into the schema:  %s.  The later matching " +
                    "rule use definition will be used");


    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTALL_DEFAULT_CACHE,
                    "An unexpected error occurred that prevented the server " +
                    "from installing a temporary default entry cache for " +
                    "use until the actual cache could be created from the " +
                    "configuration:  %s");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_GET_CONFIG_ENTRY,
                    "An unexpected error occurred while attempting to get " +
                    "the \"" + DN_ENTRY_CACHE_CONFIG + "\" entry, which " +
                    "holds the entry cache configuration:  %s.  No entry " +
                    "cache will be available");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_NO_CONFIG_ENTRY,
                    "The entry cache configuration entry \"" +
                    DN_ENTRY_CACHE_CONFIG + "\" does not exist in the " +
                    "Directory Server configuration.  No entry cache will " +
                    "be available until this entry is created with a valid " +
                    "entry cache configuration");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_REGISTER_ADD_LISTENER,
                    "An error occurred while attempting to register an " +
                    "add listener to watch for the entry cache configuration " +
                    "entry to be created:  %s.  If an entry cache " +
                    "configuration is added while the server is online, it " +
                    "will not be detected until the server is restarted");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_REGISTER_DELETE_LISTENER,
                    "An error occurred while attempting to register a " +
                    "delete listener to watch for the entry cache " +
                    "configuration entry to be deleted: %s.  If the entry " +
                    "cache configuration entry is deleted while the server " +
                    "is online, it will not be detected until the server is " +
                    "restarted");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_DESCRIPTION_CACHE_ENABLED,
                    "Indicates whether the Directory Server entry cache " +
                    "should be enabled.  If the entry cache is enabled, it " +
                    "may significantly improve performance by allowing " +
                    "previously-accessed entries to be retrieved from memory " +
                    "rather than needing to access the backend repository.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately, but will have the side effect " +
                    "of clearing the cache contents, which may result in " +
                    "degraded performance for a period of time");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_NO_ENABLED_ATTR,
                    "Configuration entry \"" + DN_ENTRY_CACHE_CONFIG +
                    "\" does not contain a value for attribute " +
                    ATTR_ENTRYCACHE_ENABLED + ", which indicates whether " +
                    "the entry cache is enabled for use in the server.  As a " +
                    "result, the cache will be disabled");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_DISABLED,
                    "The Directory Server entry cache configured in entry \"" +
                    DN_ENTRY_CACHE_CONFIG + "\" has been disabled.  No entry " +
                    "cache will be available within the server");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_UNABLE_TO_DETERMINE_ENABLED_STATE,
                    "An unexpected error occurred while attempting to " +
                    "determine whether the entry cache configured in entry \"" +
                    DN_ENTRY_CACHE_CONFIG + "\" is enabled:  %s.  As a " +
                    "result, the entry cache will be disabled");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_DESCRIPTION_CACHE_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that should provide the entry cache implementation.  " +
                    "Changes to this configuration attribute will take " +
                    "effect immediately, but will have the side effect of " +
                    "clearing the cache contents, which may result in " +
                    "degraded performance for a period of time");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_NO_CLASS_ATTR,
                    "Configuration entry \"" + DN_ENTRY_CACHE_CONFIG +
                    "\" does not contain a value for attribute " +
                    ATTR_ENTRYCACHE_CLASS + "\", which specifies the class " +
                    "name for the entry cache implementation.  As a result, " +
                    "the entry cache will be disabled");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_DETERMINE_CLASS,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_ENTRYCACHE_CLASS +
                    " attribute in configuration entry \"" +
                    DN_ENTRY_CACHE_CONFIG + "\":  %s.  The entry cache will " +
                    "be disabled");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_LOAD_CLASS,
                    "The class %s defined in attribute " +
                    ATTR_ENTRYCACHE_CLASS + " of configuration entry \"" +
                    DN_ENTRY_CACHE_CONFIG + "\" could not be loaded:  %s.  " +
                    "The entry cache will be disabled");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_INSTANTIATE_CLASS,
                    "The class %s defined in attribute " +
                    ATTR_ENTRYCACHE_CLASS + " of configuration entry \"" +
                    DN_ENTRY_CACHE_CONFIG + "\" could not be instantiated " +
                    "as a Directory Server entry cache:  %s.  As a result, " +
                    "the entry cache will be disabled");
    registerMessage(MSGID_CONFIG_ENTRYCACHE_CANNOT_INITIALIZE_CACHE,
                    "An error occurred while attempting to initialize " +
                    "an instance of class %s for use as the Directory Server " +
                    "entry cache:  %s.  As a result, the entry cache will be " +
                    "disabled");


    registerMessage(MSGID_CONFIG_LOGGER_NO_ROTATION_POLICY,
                    "No file rotation policy has been defined in " +
                    "configuration entry %s. No log rotation will take place");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY,
                    "An invalid file rotation policy %s has been defined in " +
                    "configuration entry %s");
    registerMessage(MSGID_CONFIG_LOGGER_NO_SIZE_LIMIT,
                    "No size limit has been defined for the size based file " +
                    "rotation policy in the configuration entry %s");
    registerMessage(MSGID_CONFIG_LOGGER_NO_TIME_LIMIT,
                    "No time limit has been defined for the time based file " +
                    "rotation policy in the configuration entry %s");

    registerMessage(MSGID_CONFIG_LOGGER_NO_RETENTION_POLICY,
                    "No file retention policy has been defined in " +
                    "configuration entry %s. No log files will be deleted");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY,
                    "An invalid file retention policy %s has been defined in " +
                    "configuration entry %s");
    registerMessage(MSGID_CONFIG_LOGGER_NO_NUMBER_OF_FILES,
                    "No file number limit has been defined for the " +
                    "retention policy in the configuration entry %s");
    registerMessage(MSGID_CONFIG_LOGGER_NO_DISK_SPACE_USED,
                    "No disk space limit has been defined for the " +
                    "retention policy in the configuration entry %s");
    registerMessage(MSGID_CONFIG_LOGGER_NO_FREE_DISK_SPACE,
                    "No disk space limit has been defined for the " +
                    "retention policy in the configuration entry %s");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_JAVA5_POLICY,
                    "The free disk space based retention policy " +
                    "in the configuration entry %s. is not allowed for " +
                    "the Directory Server when running on pre Java 6 VMs");

    registerMessage(MSGID_CONFIG_PLUGIN_CANNOT_GET_CONFIG_BASE,
                    "An error occurred while trying to retrieve " +
                    "configuration entry " + DN_PLUGIN_BASE + " in order to " +
                    "initialize the Directory Server plugins:  %s");
    registerMessage(MSGID_CONFIG_PLUGIN_BASE_DOES_NOT_EXIST,
                    "The entry " + DN_PLUGIN_BASE +  " does not appear to " +
                    "exist in the Directory Server configuration.  This is a " +
                    "required entry");
    registerMessage(MSGID_CONFIG_PLUGIN_ENTRY_DOES_NOT_HAVE_PLUGIN_CONFIG,
                    "Configuration entry %s exists below the plugin " +
                    "configuration root of " + DN_PLUGIN_BASE + " but does " +
                    "not have objectclass " + OC_BACKEND + " that is " +
                    "required for a Directory Server plugin.  This " +
                    "configuration entry will be ignored");
    registerMessage(MSGID_CONFIG_PLUGIN_ERROR_INTERACTING_WITH_PLUGIN_ENTRY,
                    "An unexpected error occurred while interacting with " +
                    "backend configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_ENABLED,
                    "Indicates whether this plugin should be enabled for " +
                    "use in the Directory Server.  This may be altered while " +
                    "the Directory Server is online, and will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_PLUGIN_NO_ENABLED_ATTR,
                    "Plugin configuration entry %s does not contain " +
                    "attribute " + ATTR_PLUGIN_ENABLED + ", which is used " +
                    "to indicate whether the plugin should be enabled or " +
                    "disabled.  Without this attribute, it will default to " +
                    "being disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_DISABLED,
                    "The plugin defined in configuration entry %s is " +
                    "marked as disabled and therefore will not be used");
    registerMessage(MSGID_CONFIG_PLUGIN_UNABLE_TO_DETERMINE_ENABLED_STATE,
                    "An unexpected error occurred while attempting to " +
                    "determine whether the plugin associated with " +
                    "configuration entry %s should be enabled or disabled:  " +
                    "%s.  It will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_PLUGIN_TYPE,
                    "Specifies the plugin type(s) for this plugin, which "+
                    "control the times when this plugin will be invoked " +
                    "during processing.  This value is only read when " +
                    "the plugin is loaded and initialized, so changes to " +
                    "this attribute will not take effect until the plugin " +
                    "is disabled and re-enabled, or until the server is " +
                    "restarted");
    registerMessage(MSGID_CONFIG_PLUGIN_NO_PLUGIN_TYPES,
                    "Plugin configuration entry %s does not contain " +
                    "attribute " + ATTR_PLUGIN_TYPE + ", which is used to " +
                    "specify the name(s) of the plugin type(s) for the " +
                    "plugin.  This is a required attribute, so this plugin " +
                    "will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_INVALID_PLUGIN_TYPE,
                    "Plugin configuration entry %s has an invalid value %s " +
                    "for attribute " + ATTR_PLUGIN_TYPE + " that does not " +
                    "name a valid plugin type.  This plugin will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_CANNOT_GET_PLUGIN_TYPES,
                    "An unexpected error occurred while trying to " +
                    "determine the set of plugin types for the plugin " +
                    "defined in configuration entry %s:  %s.  This plugin " +
                    "will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_ATTR_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that should provide the core logic for this plugin " +
                    "implementation.  Changes to this configuration " +
                    "attribute will not take effect until the plugin is "+
                    "disabled and re-enabled or the server is restarted");
    registerMessage(MSGID_CONFIG_PLUGIN_NO_CLASS_ATTR,
                    "Plugin configuration entry %s does not contain " +
                    "attribute " + ATTR_PLUGIN_CLASS + ", which is used to " +
                    "specify the name of the class used to provide the " +
                    "plugin implementation.  The plugin associated with " +
                    "this configuration entry will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_CANNOT_GET_CLASS,
                    "An unexpected error occurred while trying to determine " +
                    "the name of the Java class that contains the " +
                    "implementation for plugin %s:  %s.  This plugin will " +
                    "be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_CANNOT_INSTANTIATE,
                    "The Directory Server was unable to load class %s and " +
                    "use it to create a plugin instance as defined in " +
                    "configuration entry %s.  The error that occurred was:  " +
                    "%s.  This plugin will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_CANNOT_INITIALIZE,
                    "An error occurred while attempting to initialize an " +
                    "instance of class %s as a Directory Server plugin using " +
                    "the information in configuration entry %s:  %s.  This " +
                    "plugin will be disabled");
    registerMessage(MSGID_CONFIG_PLUGIN_EMPTY_ELEMENT_IN_ORDER,
                    "The plugin order definition for plugins of type %s " +
                    "contains an empty element.  This may cause the plugin " +
                    "order to be evaluated incorrectly");
    registerMessage(MSGID_CONFIG_PLUGIN_MULTIPLE_WILDCARDS_IN_ORDER,
                    "The plugin order definition for plugins of type %s " +
                    "contains multiple wildcard characters.  All plugin " +
                    "definitions should contain exactly one wildcard element " +
                    "to indicate where unmatched plugins should be included " +
                    "in the order, and including multiple wildcards may " +
                    "cause the plugin order to be evaluated incorrectly");
    registerMessage(MSGID_CONFIG_PLUGIN_LISTED_MULTIPLE_TIMES,
                    "The plugin order definition for plugins of type %s " +
                    "includes multiple references to the '%s' plugin.  This " +
                    "may cause the plugin order to be evaluated incorrectly");
    registerMessage(MSGID_CONFIG_PLUGIN_ORDER_NO_WILDCARD,
                    "The plugin order definition for plugins of type %s " +
                    "does not include a wildcard element to indicate where " +
                    "unmatched plugins should be included in the order.  The " +
                    "server will default to invoking all unnamed plugins " +
                    "after set of named plugins");
    registerMessage(MSGID_CONFIG_PLUGIN_CLASS_ACTION_REQUIRED,
                    "The requested change in the plugin class name from %s " +
                    "to %s in configuration entry %s cannot be dynamically " +
                    "applied.  This change will not take effect until the " +
                    "plugin is disabled and re-enabled or the Directory " +
                    "Server is restarted");


    registerMessage(MSGID_CONFIG_EXTOP_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid extended operation handler " +
                    "implementation:  %s");
    registerMessage(MSGID_CONFIG_EXTOP_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as an extended operation handler " +
                    "as defined in configuration entry %s:  %s");


    registerMessage(MSGID_CONFIG_SASL_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the SASL " +
                    "mechanism handler base entry " +
                    DN_SASL_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_SASL_BASE_DOES_NOT_EXIST,
                    "The SASL mechanism configuration base " +
                    DN_SASL_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_SASL_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid SASL " +
                    "mechanism handler configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_SASL_CANNOT_CREATE_HANDLER,
                    "An error occurred while attempting to create a " +
                    "Directory Server SASL mechanism handler from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_SASL_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_SASL_MECHANISM_HANDLER + " objectclass, which is " +
                    "required for SASL mechanism handler definitions");
    registerMessage(MSGID_CONFIG_SASL_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server SASL mechanism handler.  If this " +
                    "is altered while the associated handler is enabled, " +
                    "then that handler must be disabled and re-enabled for " +
                    "the change to take effect");
    registerMessage(MSGID_CONFIG_SASL_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_SASL_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated SASL mechanism handler");
    registerMessage(MSGID_CONFIG_SASL_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_SASL_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_SASL_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid SASL mechanism handler implementation:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_SASL_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server SASL mechanism " +
                    "handler should be enabled.  Changes to this attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_CONFIG_SASL_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_SASL_ENABLED +
                    " which indicates whether the SASL mechanism handler " +
                    "should be enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_SASL_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_SASL_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_SASL_CLASS_ACTION_REQUIRED,
                    "The requested change in the SASL mechanism handler " +
                    "class name from %s to %s in configuration entry %s " +
                    "cannot be dynamically applied.  This change will not " +
                    "take effect until the handler is disabled and " +
                    "re-enabled or the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_SASL_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a SASL mechanism handler as " +
                    "defined in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_SASL_EXISTS,
                    "Unable to add a new SASL mechanism handler entry with " +
                    "DN %s because there is already a handler registered " +
                    "with that DN");
    registerMessage(MSGID_CONFIG_SASL_UNACCEPTABLE_CONFIG,
                    "The configuration for the SASL mechanism handler " +
                    "defined in configuration entry %s was not acceptable " +
                    "according to its internal validation.  However, no " +
                    "specific information is available regarding the " +
                    "problem(s) with the entry");
    registerMessage(MSGID_CONFIG_LOGGER_INVALID_SUPPRESS_INT_OPERATION_VALUE,
        "Invalid value specified for attribute %s. " +
        "Allowed values are true or false");
    registerMessage(MSGID_CONFIG_LOGGER_SUPPRESS_INTERNAL_OPERATIONS,
                    "Indicates whether messages for internal operations " +
                    "should be excluded from the access log file");


    registerMessage(MSGID_CONFIG_KEYMANAGER_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the key " +
                    "manager provider base entry " +
                    DN_KEYMANAGER_PROVIDER_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_KEYMANAGER_BASE_DOES_NOT_EXIST,
                    "The key manager provider configuration base " +
                    DN_KEYMANAGER_PROVIDER_CONFIG_BASE + " does not exist in " +
                    "the Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_KEYMANAGER_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid key " +
                    "manager provider configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_KEYMANAGER_CANNOT_CREATE_PROVIDER,
                    "An error occurred while attempting to create a " +
                    "Directory Server key manager provider from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_KEYMANAGER_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_KEY_MANAGER_PROVIDER + " objectclass, which is " +
                    "required for key manager provider definitions");
    registerMessage(MSGID_CONFIG_KEYMANAGER_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_KEYMANAGER_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated key manager provider");
    registerMessage(MSGID_CONFIG_KEYMANAGER_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_KEYMANAGER_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_KEYMANAGER_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid key manager provider implementation:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_KEYMANAGER_DESCRIPTION_ENABLED,
                    "Indicates whether the Directory Server key manager " +
                    "provider should be enabled.  A key manager provider is " +
                    "required for operations that require access to a " +
                    "key manager (e.g., communication over SSL).  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately, but will only impact future attempts to " +
                    "access the key manager");
    registerMessage(MSGID_CONFIG_KEYMANAGER_NO_ENABLED_ATTR,
                    "Configuration entry \"%s\" does not contain a value for " +
                    "attribute " + ATTR_KEYMANAGER_ENABLED +
                    ", which indicates whether the key manager provider is " +
                    "enabled for use in the server");
    registerMessage(MSGID_CONFIG_KEYMANAGER_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_KEYMANAGER_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_KEYMANAGER_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that includes the key manager provider implementation.  " +
                    "Changes to this configuration attribute will not take " +
                    "effect until the key manager provider has been disabled " +
                    "and then re-enabled, or until the server is restarted");
    registerMessage(MSGID_CONFIG_KEYMANAGER_CLASS_ACTION_REQUIRED,
                    "The requested change in the key manager provider class " +
                    "name from %s to %s in configuration entry %s cannot be " +
                    "dynamically applied.  This change will not take effect " +
                    "until the provider is disabled and re-enabled or the " +
                    "Directory Server is restarted");
    registerMessage(MSGID_CONFIG_KEYMANAGER_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a key manager provider as " +
                    "defined in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_KEYMANAGER_EXISTS,
                    "Unable to add a new key manager provider entry with DN " +
                    "%s because there is already a provider registered with " +
                    "that DN");
    registerMessage(MSGID_CONFIG_KEYMANAGER_UNACCEPTABLE_CONFIG,
                    "The configuration for the key manager provider defined " +
                    "in configuration entry %s was not acceptable according " +
                    "to its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_TRUSTMANAGER_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "trust manager provider base entry " +
                    DN_TRUSTMANAGER_PROVIDER_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_BASE_DOES_NOT_EXIST,
                    "The trust manager provider configuration base " +
                    DN_TRUSTMANAGER_PROVIDER_CONFIG_BASE + " does not exist " +
                    "in the Directory Server configuration.  This entry must " +
                    "be present for the server to function properly");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid trust " +
                    "manager provider configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_CANNOT_CREATE_PROVIDER,
                    "An error occurred while attempting to create a " +
                    "Directory Server trust manager provider from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_TRUST_MANAGER_PROVIDER + " objectclass, which is " +
                    "required for trust manager provider definitions");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_TRUSTMANAGER_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated trust manager provider");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_TRUSTMANAGER_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid trust manager provider implementation:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_DESCRIPTION_ENABLED,
                    "Indicates whether the Directory Server trust manager " +
                    "provider should be enabled.  A trust manager provider " +
                    "is required for operations that require access to a " +
                    "trust manager (e.g., communication over SSL).  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately, but will only impact future attempts to " +
                    "access the trust manager");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_NO_ENABLED_ATTR,
                    "Configuration entry \"%s\" does not contain a value for " +
                    "attribute " + ATTR_TRUSTMANAGER_ENABLED +
                    ", which indicates whether the trust manager provider is " +
                    "enabled for use in the server");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_TRUSTMANAGER_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that includes the trust manager provider " +
                    "implementation.  Changes to this configuration " +
                    "attribute will not take effect until the trust manager " +
                    "provider has been disabled and then re-enabled, or " +
                    "until the server is restarted");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_CLASS_ACTION_REQUIRED,
                    "The requested change in the trust manager provider " +
                    "class name from %s to %s in configuration entry %s " +
                    "cannot be dynamically applied.  This change will not " +
                    "take effect until the provider is disabled and " +
                    "re-enabled or the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a trust manager provider as " +
                    "defined in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_EXISTS,
                    "Unable to add a new trust manager provider entry with " +
                    "DN %s because there is already a provider registered " +
                    "with that DN");
    registerMessage(MSGID_CONFIG_TRUSTMANAGER_UNACCEPTABLE_CONFIG,
                    "The configuration for the trust manager provider " +
                    "defined in configuration entry %s was not acceptable " +
                    "according to its internal validation.  However, no " +
                    "specific information is available regarding the " +
                    "problem(s) with the entry");


    registerMessage(MSGID_CONFIG_CERTMAPPER_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "certificate mapper base entry " +
                    DN_CERTMAPPER_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_CERTMAPPER_BASE_DOES_NOT_EXIST,
                    "The certificate mapper configuration base " +
                    DN_CERTMAPPER_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_CERTMAPPER_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "certificate mapper configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_CERTMAPPER_CANNOT_CREATE_MAPPER,
                    "An error occurred while attempting to create a " +
                    "Directory Server certificate mapper from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_CERTMAPPER_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_CERTIFICATE_MAPPER + " objectclass, which is required " +
                    "for certificate mapper definitions");
    registerMessage(MSGID_CONFIG_CERTMAPPER_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_CERTMAPPER_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated certificate mapper");
    registerMessage(MSGID_CONFIG_CERTMAPPER_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_CERTMAPPER_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_CERTMAPPER_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid certificate mapper implementation:  %s");
    registerMessage(MSGID_CONFIG_CERTMAPPER_DESCRIPTION_ENABLED,
                    "Indicates whether the Directory Server certificate " +
                    "mapper should be enabled.  A certificate mapper is " +
                    "used to establish a mapping between a client " +
                    "certificate chain and a user entry in the Directory " +
                    "Server for SASL EXTERNAL authentication and similar " +
                    "purposes.  Changes to this configuration attribute will " +
                    "take effect immediately");
    registerMessage(MSGID_CONFIG_CERTMAPPER_NO_ENABLED_ATTR,
                    "Configuration entry \"%s\" does not contain a value for " +
                    "attribute " + ATTR_CERTMAPPER_ENABLED +
                    ", which indicates whether the certificate mapper is " +
                    "enabled for use in the server");
    registerMessage(MSGID_CONFIG_CERTMAPPER_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_CERTMAPPER_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_CERTMAPPER_DESCRIPTION_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that includes the certificate mapper implementation.  " +
                    "Changes to this configuration attribute will not take " +
                    "effect until the certificate mapper has been disabled " +
                    "and then re-enabled, or until the server is restarted");
    registerMessage(MSGID_CONFIG_CERTMAPPER_CLASS_ACTION_REQUIRED,
                    "The requested change in the certificate mapper class " +
                    "name from %s to %s in configuration entry %s cannot be " +
                    "dynamically applied.  This change will not take effect " +
                    "until the mapper is disabled and re-enabled or the " +
                    "Directory Server is restarted");
    registerMessage(MSGID_CONFIG_CERTMAPPER_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a certificate mapper as defined " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_CERTMAPPER_EXISTS,
                    "Unable to add a new certificate mapper entry with DN %s " +
                    "because there is already a mapper registered with that " +
                    "DN");
    registerMessage(MSGID_CONFIG_CERTMAPPER_UNACCEPTABLE_CONFIG,
                    "The configuration for the certificate mapper defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_PWSCHEME_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "password storage scheme base entry " +
                    DN_PWSCHEME_CONFIG_BASE + " from the Directory Server " +
                    "configuration:  %s");
    registerMessage(MSGID_CONFIG_PWSCHEME_BASE_DOES_NOT_EXIST,
                    "The password storage scheme configuration base " +
                    DN_PWSCHEME_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_PWSCHEME_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "password storage scheme configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_PWSCHEME_CANNOT_CREATE_SCHEME,
                    "An error occurred while attempting to create a " +
                    "Directory Server password storage scheme from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PWSCHEME_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_PASSWORD_STORAGE_SCHEME + " objectclass, which is " +
                    "required for password storage scheme definitions");
    registerMessage(MSGID_CONFIG_PWSCHEME_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server password storage scheme.  If this " +
                    "is altered while the associated scheme is enabled, " +
                    "then that storage scheme must be disabled and " +
                    "re-enabled for the change to take effect");
    registerMessage(MSGID_CONFIG_PWSCHEME_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_PWSCHEME_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated password storage scheme");
    registerMessage(MSGID_CONFIG_PWSCHEME_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_PWSCHEME_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_PWSCHEME_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid password storage scheme " +
                    "implementation:  %s");
    registerMessage(MSGID_CONFIG_PWSCHEME_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server password " +
                    "storage scheme should be enabled.  Changes to this " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_PWSCHEME_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_PWSCHEME_ENABLED +
                    " which indicates whether the password storage scheme " +
                    "should be enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_PWSCHEME_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_PWSCHEME_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_PWSCHEME_CLASS_ACTION_REQUIRED,
                    "The requested change in the password storage scheme " +
                    "class name from %s to %s in configuration entry %s " +
                    "cannot be dynamically applied.  This change will not " +
                    "take effect until the storage scheme is disabled and " +
                    "re-enabled or the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_PWSCHEME_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a password storage scheme as " +
                    "defined in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PWSCHEME_EXISTS,
                    "Unable to add a new password storage scheme entry with " +
                    "DN %s because there is already a storage scheme " +
                    "registered with that DN");
    registerMessage(MSGID_CONFIG_PWSCHEME_UNACCEPTABLE_CONFIG,
                    "The configuration for the password storage scheme " +
                    "defined in configuration entry %s was not acceptable " +
                    "according to its internal validation.  However, no " +
                    "specific information is available regarding the " +
                    "problem(s) with the entry");


    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_GET_MAC,
                    "An error occurred while attempting to obtain the %s MAC " +
                    "provider to create the signed hash for the backup:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_GET_DIGEST,
                    "An error occurred while attempting to obtain the %s " +
                    "message digest to create the hash for the backup:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_CREATE_ARCHIVE_FILE,
                    "An error occurred while trying to create the config " +
                    "archive file %s in directory %s:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_GET_CIPHER,
                    "An error occurred while attempting to obtain the %s " +
                    "cipher to use to encrypt the backup:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_ZIP_COMMENT,
                    "%s config backup %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_DETERMINE_CONFIG_FILE_LOCATION,
                    "An error occurred while attempting to determine the " +
                    "path to the Directory Server configuration file so that " +
                    "it could be archived:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_BACKUP_CONFIG_FILE,
                    "An error occurred while attempting to back up " +
                    "configuration file %s:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_BACKUP_ARCHIVED_CONFIGS,
                    "An error occurred while attempting to back up the " +
                    "archived previous configurations from file %s:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_CLOSE_ZIP_STREAM,
                    "An error occurred while trying to close the config " +
                    "archive file %s in directory %s:  %s");
    registerMessage(MSGID_CONFIG_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR,
                    "An error occurred while attempting to update the backup " +
                    "descriptor file %s with information about the " +
                    "configuration backup:  %s");


    registerMessage(MSGID_CONFIG_RESTORE_NO_SUCH_BACKUP,
                    "Unable to restore or verify configuration backup %s in " +
                    "directory %s because no such backup exists");
    registerMessage(MSGID_CONFIG_RESTORE_NO_BACKUP_FILE,
                    "Unable to restore or verify configuration backup %s in " +
                    "directory %s because the archive filename could not be " +
                    "determined");
    registerMessage(MSGID_CONFIG_RESTORE_NO_SUCH_FILE,
                    "Unable to restore or verify configuration backup %s " +
                    "because the specified archive file %s does not exist");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_CHECK_FOR_ARCHIVE,
                    "Unable to restore or verify configuration backup %s " +
                    "because an error occurred while trying to determine " +
                    "whether backup archive %s exists:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_UNKNOWN_DIGEST,
                    "Unable to restore or verify configuration backup %s " +
                    "because an unsigned hash of this backup is available " +
                    "but the server cannot determine the digest algorithm " +
                    "used to generate this hash");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_GET_DIGEST,
                    "Unable to restore or verify configuration backup %s " +
                    "because it has an unsigned hash that uses an unknown or " +
                    "unsupported digest algorithm of %s");
    registerMessage(MSGID_CONFIG_RESTORE_UNKNOWN_MAC,
                    "Unable to restore or verify configuration backup %s " +
                    "because a signed hash of this backup is available but " +
                    "the server cannot determine the MAC algorithm used to " +
                    "generate this hash");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_GET_MAC,
                    "Unable to restore or verify configuration backup %s " +
                    "because it has a signed hash that uses an unknown or " +
                    "unsupported MAC algorithm of %s");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_OPEN_BACKUP_FILE,
                    "Unable to restore or verify configuration backup %s " +
                    "because an error occurred while attempting to open the " +
                    "backup archive file %s:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_UNKNOWN_CIPHER,
                    "Unable to restore or verify configuration backup %s " +
                    "because it is encrypted but the server cannot determine " +
                    "the cipher used to perform this encryption");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_GET_CIPHER,
                    "Unable to restore or verify configuration backup %s " +
                    "because it is encrypted using an unknown or unsupported " +
                    "cipher of %s");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_BACKUP_EXISTING_CONFIG,
                    "Unable to restore configuration backup %s because an " +
                    "error occurred while attempting to temporarily back up " +
                    "the current configuration files from %s to %s:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_RESTORED_OLD_CONFIG,
                    "An error occurred that prevented the configuration " +
                    "backup from being properly restored.  However, the " +
                    "original configuration files that were in place before " +
                    "the start of the restore process have been preserved " +
                    "and are now in their original location of %s");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_RESTORE_OLD_CONFIG,
                    "An error occurred that prevented the configuration " +
                    "backup from being properly restored.  The original " +
                    "configuration files that were in place before the start " +
                    "of the restore process have been preserved and are " +
                    "contained in the %s directory");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_CREATE_CONFIG_DIRECTORY,
                    "Unable to restore configuration backup %s because an " +
                    "error occurred while attempting to create a new empty " +
                    "directory %s into which the files should be restored:  " +
                    "%s");
    registerMessage(MSGID_CONFIG_RESTORE_OLD_CONFIG_SAVED,
                    "An error occurred that prevented the configuration " +
                    "backup from being properly restored.  The original " +
                    "configuration files that were in place before the start " +
                    "of the restore process have been preserved in the %s " +
                    "directory");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_GET_ZIP_ENTRY,
                    "Unable to restore or verify configuration backup %s " +
                    "because an error occurred while trying to read the next " +
                    "entry from the archive file %s:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_CREATE_FILE,
                    "Unable to restore configuration backup %s because an " +
                    "error occurred while trying to recreate file %s:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE,
                    "Unable to restore or verify configuration backup %s " +
                    "because an error occurred while processing archived " +
                    "file %s:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE,
                    "Unable to restore or verify configuration backup %s " +
                    "because an unexpected error occurred while trying to " +
                    "close the archive file %s:  %s");
    registerMessage(MSGID_CONFIG_RESTORE_UNSIGNED_HASH_VALID,
                    "The message digest calculated from the backup archive " +
                    "matches the digest stored with the backup information");
    registerMessage(MSGID_CONFIG_RESTORE_UNSIGNED_HASH_INVALID,
                    "Unable to restore or verify configuration backup %s " +
                    "because the message digest calculated from the backup " +
                    "archive does not match the digest stored with the " +
                    "backup information");
    registerMessage(MSGID_CONFIG_RESTORE_SIGNED_HASH_VALID,
                    "The signed digest calculated from the backup archive " +
                    "matches the signature stored with the backup " +
                    "information");
    registerMessage(MSGID_CONFIG_RESTORE_SIGNED_HASH_INVALID,
                    "Unable to restore or verify configuration backup %s " +
                    "because the signed digest calculated from the backup " +
                    "archive does not match the signature stored with the " +
                    "backup information");
    registerMessage(MSGID_CONFIG_RESTORE_VERIFY_SUCCESSFUL,
                    "All tests performed on configuration backup %s from " +
                    "directory %s show that the archive appears to be valid");
    registerMessage(MSGID_CONFIG_RESTORE_SUCCESSFUL,
                    "Configuration backup %s was successfully restored from " +
                    "the archive in directory %s");


    registerMessage(MSGID_CONFIG_IDMAPPER_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "identity mapper base entry " + DN_IDMAPPER_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_IDMAPPER_BASE_DOES_NOT_EXIST,
                    "The identity mapper configuration base " +
                    DN_IDMAPPER_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_IDMAPPER_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "identity mapper configuration:  %s.  It will be ignored");
    registerMessage(MSGID_CONFIG_IDMAPPER_CANNOT_CREATE_MAPPER,
                    "An error occurred while attempting to create a " +
                    "Directory Server identity mapper from the information " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_IDMAPPER_NO_PROXY_MAPPER_DN,
                    "The Directory Server does not have any identity mapper " +
                    "configured for use in conjunction with proxied " +
                    "authorization V2 operations.  The Directory Server " +
                    "will not be able to process requests containing the " +
                    "proxied authorization control with a username-based " +
                    "authorization ID");
    registerMessage(MSGID_CONFIG_IDMAPPER_INVALID_PROXY_MAPPER_DN,
                    "The configured proxied authorization identity mapper DN " +
                    "%s does not refer to an active identity mapper.  The " +
                    "Directory Server will not be able to process requests " +
                    "containing the proxied authorization control with a " +
                    "username-based authorization ID");
    registerMessage(MSGID_CONFIG_IDMAPPER_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_IDENTITY_MAPPER + " objectclass, which is " +
                    "required for identity mapper definitions");
    registerMessage(MSGID_CONFIG_IDMAPPER_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "a Directory Server identity mapper.  If this is altered " +
                    "while the associated identity mapper is enabled, then " +
                    "that mapper must be disabled and re-enabled for the " +
                    "change to take effect");
    registerMessage(MSGID_CONFIG_IDMAPPER_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_IDMAPPER_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated identity mapper");
    registerMessage(MSGID_CONFIG_IDMAPPER_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_IDMAPPER_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_IDMAPPER_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid identity mapper implementation:  %s");
    registerMessage(MSGID_CONFIG_IDMAPPER_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server identity mapper " +
                    "should be enabled.  Changes to this attribute will take " +
                    "effect immediately");
    registerMessage(MSGID_CONFIG_IDMAPPER_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_IDMAPPER_ENABLED +
                    " which indicates whether the identity mapper should be " +
                    "enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_IDMAPPER_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_IDMAPPER_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_IDMAPPER_CLASS_ACTION_REQUIRED,
                    "The requested change in the identity mapper class name " +
                    "from %s to %s in configuration entry %s cannot be " +
                    "dynamically applied.  This change will not take effect " +
                    "until the identity mapper is disabled and re-enabled or " +
                    "the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_IDMAPPER_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as an identity mapper as defined " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_IDMAPPER_EXISTS,
                    "Unable to add a new identity mapper entry with DN %s " +
                    "because there is already an identity mapper registered " +
                    "with that DN");
    registerMessage(MSGID_CONFIG_IDMAPPER_UNACCEPTABLE_CONFIG,
                    "The configuration for the identity mapper defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_SYNCH_CANNOT_GET_CONFIG_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "Directory Server synchronization provider configuration " +
                    "base entry " + DN_SYNCHRONIZATION_PROVIDER_BASE +
                    ":  %s");
    registerMessage(MSGID_CONFIG_SYNCH_BASE_DOES_NOT_EXIST,
                    "The Directory Server synchronization provider " +
                    "base entry " + DN_SYNCHRONIZATION_PROVIDER_BASE +
                    " does not exist.  This entry must be  present in the " +
                    "Directory Server configuration");
    registerMessage(MSGID_CONFIG_SYNCH_ENTRY_DOES_NOT_HAVE_PROVIDER_CONFIG,
                    "Configuration entry %s exists below the Directory " +
                    "Server synchronization provider root but does not " +
                    "contain attribute " + OC_SYNCHRONIZATION_PROVIDER +
                    " which must be present in all synchronization provider " +
                    "configuration entries");
    registerMessage(MSGID_CONFIG_SYNCH_CANNOT_CHECK_FOR_PROVIDER_CONFIG_OC,
                    "An error occurred while attempting to determine whether " +
                    "configuration entry %s was a valid Directory Server " +
                    "synchronization provider:  %s");
    registerMessage(MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_ENABLED,
                    "Indicates whether the associated Directory Server " +
                    "synchronization provider is enabled and should be used " +
                    "by the server.  This attribute must be present in all " +
                    "synchronization provider entries and may not be changed " +
                    "while the Directory Server is running");
    registerMessage(MSGID_CONFIG_SYNCH_PROVIDER_NO_ENABLED_ATTR,
                    "Synchronization provider configuration entry %s does " +
                    "not contain attribute " +
                    ATTR_SYNCHRONIZATION_PROVIDER_ENABLED +
                    " which indicates whether the synchronization provider " +
                    "is enabled for use");
    registerMessage(MSGID_CONFIG_SYNCH_PROVIDER_DISABLED,
                    "The Directory Server synchronization provider defined " +
                    "in configuration entry %s is disabled.  This " +
                    "provider will not be used");
    registerMessage(MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_ENABLED_STATE,
                    "An error occurred while attempting to determine whether " +
                    "the Directory Server synchronization provider defined " +
                    "in configuration entry %s should be enabled:  %s");
    registerMessage(MSGID_CONFIG_SYNCH_DESCRIPTION_PROVIDER_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that will provide the logic for the Directory Server " +
                    "synchronization provider.  This attribute must be " +
                    "present in all synchronization provider entries and may " +
                    "not be changed while the Directory Server is running");
    registerMessage(MSGID_CONFIG_SYNCH_NO_CLASS_ATTR,
                    "Synchronization provider configuration entry %s does " +
                    "not contain attribute " +
                    ATTR_SYNCHRONIZATION_PROVIDER_CLASS +
                    " which specifies the name of the class that implements " +
                    "the synchronization provider logic");
    registerMessage(MSGID_CONFIG_SYNCH_UNABLE_TO_DETERMINE_CLASS,
                    "An error occurred while attempting to determine the " +
                    "name of the class used to provide the Directory Server " +
                    "synchronization provider logic from configuration " +
                    "entry %s:  %s");
    registerMessage(MSGID_CONFIG_SYNCH_UNABLE_TO_LOAD_PROVIDER_CLASS,
                    "An error occurred while attempting to load class %s " +
                    "referenced in synchronization provider configuration " +
                    "entry %s:  %s");
    registerMessage(MSGID_CONFIG_SYNCH_UNABLE_TO_INSTANTIATE_PROVIDER,
                    "An error occurred while attempting to instantiate " +
                    "class %s referenced in synchronization provider " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_SYNCH_ERROR_INITIALIZING_PROVIDER,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server synchronization provider referenced " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_SYNCH_PROVIDER_HAS_BEEN_DISABLED,
                    "The synchronization provider defined in configuration " +
                    "entry %s is currently enabled but the configuration has " +
                    "changed so that it should be disabled.  This will not " +
                    "take effect until the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_SYNCH_PROVIDER_CLASS_CHANGED,
                    "The Java class providing the logic for the " +
                    "synchronization provider defined in configuration entry " +
                    "%s has changed from %s to %s.  This will not take " +
                    "effect until the Directory Server is restarted");


    registerMessage(MSGID_CONFIG_PWVALIDATOR_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "password validator base entry " +
                    DN_PWVALIDATOR_CONFIG_BASE + " from the Directory Server " +
                    "configuration:  %s");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_BASE_DOES_NOT_EXIST,
                    "The password validator configuration base " +
                    DN_PWVALIDATOR_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "password validator configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_CANNOT_CREATE_VALIDATOR,
                    "An error occurred while attempting to create a " +
                    "Directory Server password validator from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_PASSWORD_VALIDATOR + " objectclass, which is required " +
                    "for password validator definitions");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server password validator.  If this is " +
                    "altered while the associated validator is enabled, then " +
                    "that validator must be disabled and re-enabled for the " +
                    "change to take effect");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_PWVALIDATOR_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated password validator");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_PWVALIDATOR_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid password validator implementation:  %s");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server password " +
                    "validator should be enabled.  Changes to this attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_PWVALIDATOR_ENABLED +
                    " which indicates whether the password validator should " +
                    "be enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_PWVALIDATOR_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_CLASS_ACTION_REQUIRED,
                    "The requested change in the password validator class " +
                    "name from %s to %s in configuration entry %s cannot be " +
                    "dynamically applied.  This change will not take effect " +
                    "until the validator is disabled and re-enabled or the " +
                    "Directory Server is restarted");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a password validator as defined " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_EXISTS,
                    "Unable to add a new password validator entry with DN %s " +
                    "because there is already a validator registered with " +
                    "that DN");
    registerMessage(MSGID_CONFIG_PWVALIDATOR_UNACCEPTABLE_CONFIG,
                    "The configuration for the password validator defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_PWGENERATOR_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "password generator base entry " +
                    DN_PWGENERATOR_CONFIG_BASE + " from the Directory Server " +
                    "configuration:  %s");
    registerMessage(MSGID_CONFIG_PWGENERATOR_BASE_DOES_NOT_EXIST,
                    "The password generator configuration base " +
                    DN_PWGENERATOR_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_PWGENERATOR_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "password generator configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_PWGENERATOR_CANNOT_CREATE_GENERATOR,
                    "An error occurred while attempting to create a " +
                    "Directory Server password generator from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PWGENERATOR_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_PASSWORD_GENERATOR + " objectclass, which is required " +
                    "for password generator definitions");
    registerMessage(MSGID_CONFIG_PWGENERATOR_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server password generator.  If this is " +
                    "altered while the associated generator is enabled, then " +
                    "that generator must be disabled and re-enabled for the " +
                    "change to take effect");
    registerMessage(MSGID_CONFIG_PWGENERATOR_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_PWGENERATOR_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated password generator");
    registerMessage(MSGID_CONFIG_PWGENERATOR_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_PWGENERATOR_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_PWGENERATOR_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid password generator implementation:  %s");
    registerMessage(MSGID_CONFIG_PWGENERATOR_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server password " +
                    "generator should be enabled.  Changes to this attribute " +
                    "will take effect immediately");
    registerMessage(MSGID_CONFIG_PWGENERATOR_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " + ATTR_PWGENERATOR_ENABLED +
                    " which indicates whether the password generator should " +
                    "be enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_PWGENERATOR_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_PWGENERATOR_ENABLED + ":  %s");
    registerMessage(MSGID_CONFIG_PWGENERATOR_CLASS_ACTION_REQUIRED,
                    "The requested change in the password generator class " +
                    "name from %s to %s in configuration entry %s cannot be " +
                    "dynamically applied.  This change will not take effect " +
                    "until the generator is disabled and re-enabled or the " +
                    "Directory Server is restarted");
    registerMessage(MSGID_CONFIG_PWGENERATOR_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a password generator as defined " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_PWGENERATOR_EXISTS,
                    "Unable to add a new password generator entry with DN %s " +
                    "because there is already a generator registered with " +
                    "that DN");
    registerMessage(MSGID_CONFIG_PWGENERATOR_UNACCEPTABLE_CONFIG,
                    "The configuration for the password generator defined in " +
                    "configuration entry %s was not acceptable according to " +
                    "its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(MSGID_CONFIG_PWPOLICY_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "password policy base entry " + DN_PWPOLICY_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_PWPOLICY_BASE_DOES_NOT_EXIST,
                    "The password policy configuration base " +
                    DN_PWPOLICY_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_PWPOLICY_NO_POLICIES,
                    "No password policies have been defined below the " +
                    DN_PWPOLICY_CONFIG_BASE + " entry in the Directory " +
                    "Server configuration.  At least one password policy " +
                    "configuration must be defined");
    registerMessage(MSGID_CONFIG_PWPOLICY_NO_DEFAULT_POLICY,
                    "No default password policy is configured for the " +
                    "Directory Server.  The default password policy must be " +
                    "specified by the " + ATTR_DEFAULT_PWPOLICY_DN +
                    " attribute in the " + DN_CONFIG_ROOT + " entry");
    registerMessage(MSGID_CONFIG_PWPOLICY_INVALID_POLICY_CONFIG,
                    "The password policy defined in configuration entry %s " +
                    "is invalid:  %s");
    registerMessage(MSGID_CONFIG_PWPOLICY_MISSING_DEFAULT_POLICY,
                    "The Directory Server default password policy is " +
                    "defined as %s, but that entry does not exist or is not " +
                    "below the password policy configuration base " +
                    DN_PWPOLICY_CONFIG_BASE + "");
    registerMessage(MSGID_CONFIG_PWPOLICY_CANNOT_DELETE_DEFAULT_POLICY,
                    "The specified entry %s is currently defined as the " +
                    "configuration entry for the default password policy.  " +
                    "The default password policy configuration entry may not " +
                    "be removed");
    registerMessage(MSGID_CONFIG_PWPOLICY_REMOVED_POLICY,
                    "Password policy entry %s has been removed from the " +
                    "Directory Server configuration.  Any user entries that " +
                    "explicitly reference this password policy will no " +
                    "longer be allowed to authenticate");

    registerMessage(MSGID_CONFIG_AUTHZ_CANNOT_GET_ENTRY,
        "An error occurred while attempting to retrieve the "
            + "Directory Server access control configuration entry "
            + DN_AUTHZ_HANDLER_CONFIG + ":  %s");

    registerMessage(MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_EXIST,
        "The Directory Server access control configuration entry "
            + DN_AUTHZ_HANDLER_CONFIG
            + " does not exist.  This entry must be present in the "
            + "Directory Server configuration");

    registerMessage(MSGID_CONFIG_AUTHZ_ENTRY_DOES_NOT_HAVE_OBJECT_CLASS,
        "The Directory Server access control configuration entry "
            + DN_AUTHZ_HANDLER_CONFIG
            + " does not have the correct object class.  This entry must"
            + " have the object class " + OC_AUTHZ_HANDLER_CONFIG
            + " in order to be valid");

    registerMessage(MSGID_CONFIG_AUTHZ_DESCRIPTION_ENABLED,
        "Indicates whether access control is enabled and should be used "
            + "by the server.  This attribute is mandatory");

    registerMessage(MSGID_CONFIG_AUTHZ_NO_ENABLED_ATTR,
        "The access control configuration entry %s does "
            + "not contain attribute " + ATTR_AUTHZ_HANDLER_ENABLED
            + " which indicates whether the access control "
            + "is enabled for use");

    registerMessage(MSGID_CONFIG_AUTHZ_DISABLED,
        "Access control has been disabled");

    registerMessage(MSGID_CONFIG_AUTHZ_ENABLED,
        "Access control has been enabled and will use the %s "
            + "implementation");

    registerMessage(MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_ENABLED_STATE,
        "An error occurred while attempting to determine whether "
            + "the Directory Server access control as defined "
            + "in configuration entry %s should be enabled:  %s");

    registerMessage(MSGID_CONFIG_AUTHZ_DESCRIPTION_CLASS,
        "Specifies the fully-qualified name of the Java class "
            + "that will provide the access control implementation for "
            + "the Directory Server. This attribute is mandatory");

    registerMessage(MSGID_CONFIG_AUTHZ_NO_CLASS_ATTR,
        "The access control configuration entry %s does "
            + "not contain attribute "
            + ATTR_AUTHZ_HANDLER_CLASS
            + " which specifies the name of the Java class providing"
            + " the access control implementation for the Directory Server");

    registerMessage(MSGID_CONFIG_AUTHZ_UNABLE_TO_DETERMINE_CLASS,
        "An error occurred while attempting to determine the "
            + "name of the class used to provide the Directory Server "
            + "access control implementation from configuration "
            + "entry %s:  %s");

    registerMessage(MSGID_CONFIG_AUTHZ_UNABLE_TO_LOAD_CLASS,
        "An error occurred while attempting to load class %s "
            + "referenced in the access control configuration "
            + "entry %s:  %s");

    registerMessage(MSGID_CONFIG_AUTHZ_BAD_CLASS,
        "The access control implementation class %s "
            + "referenced in the access control configuration "
            + "entry %s does not implement the %s interface:  %s");

    registerMessage(MSGID_CONFIG_AUTHZ_UNABLE_TO_INSTANTIATE_HANDLER,
        "An error occurred while attempting to instantiate "
            + "class %s referenced in the access control configuration "
            + "entry %s:  %s");

    registerMessage(MSGID_CONFIG_AUTHZ_ERROR_INITIALIZING_HANDLER,
        "An error occurred while attempting to initialize the "
            + "Directory Server access control implementation referenced "
            + "in configuration entry %s:  %s");


    registerMessage(MSGID_CONFIG_ROOTDN_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "root DN base entry " + DN_ROOT_DN_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_ROOTDN_BASE_DOES_NOT_EXIST,
                    "The password policy configuration base " +
                    DN_ROOT_DN_CONFIG_BASE + " does not exist in the " +
                    "Directory Server configuration.  This entry must be " +
                    "present for the server to function properly");
    registerMessage(MSGID_CONFIG_ROOTDN_DESCRIPTION_ROOT_PRIVILEGE,
                    "Specifies the set of privileges that should " +
                    "automatically be assigned to root users when they " +
                    "authenticate to the server");
    registerMessage(MSGID_CONFIG_ROOTDN_UNRECOGNIZED_PRIVILEGE,
                    "The set of default root privileges contained in " +
                    "configuration attribute %s of entry %s contains an " +
                    "unrecognized privilege %s");
    registerMessage(MSGID_CONFIG_ROOTDN_ERROR_DETERMINING_ROOT_PRIVILEGES,
                    "An error occurred while attempting to determine the " +
                    "set of privileges that root users should be granted by " +
                    "default:  %s");
    registerMessage(MSGID_CONFIG_ROOTDN_UPDATED_PRIVILEGES,
                    "The set of privileges that will automatically be " +
                    "assigned to root users has been updated.  This new " +
                    "privilege set will not apply to any existing " +
                    "connection already authenticated as a root user, but " +
                    "will used for any subsequent root user authentications");
    registerMessage(MSGID_CONFIG_ROOTDN_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid root DN " +
                    "configuration:  %s.  It will be ignored");
    registerMessage(MSGID_CONFIG_ROOTDN_CANNOT_CREATE,
                    "An error occurred while attempting to create a " +
                    "Directory Server root DN from the information in " +
                    "configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_ROOTDN_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_ROOT_DN + " objectclass, which is required for " +
                    "Directory Server root DN definitions");
    registerMessage(MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN,
                    "Specifies one or more alternate bind DNs that may be " +
                    "used to authenticate as the associated root DN, in " +
                    "addition to the actual DN of the root DN configuration " +
                    "entry.  Alternate bind DNs must not conflict with the " +
                    "DNs of any other entries in the directory, nor can they " +
                    "conflict with other alternate bind DNs configured for " +
                    "other root DNs.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_ROOTDN_CONFLICTING_MAPPING,
                    "Unable to register \"%s\" as an alternate bind DN for " +
                    "user \"%s\" because it is already registered as an " +
                    "alternate bind DN for root user \"%s\"");
    registerMessage(MSGID_CONFIG_ROOTDN_CANNOT_PARSE_ALTERNATE_BIND_DNS,
                    "An error occurred while trying to parse the set of " +
                    "alternate bind DNs for root user %s:  %s");
    registerMessage(MSGID_CONFIG_ROOTDN_CANNOT_REGISTER_ALTERNATE_BIND_DN,
                    "An error occurred while trying to register\"%s\" as an " +
                    "alternate bind DN for root user \"%s\":  %s");
    registerMessage(MSGID_CONFIG_ROOTDN_EXISTS,
                    "Unable to add root DN entry %s because another root " +
                    "user is already registered with that DN");


    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "account status notification handler base entry " +
                    DN_ACCT_NOTIFICATION_HANDLER_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_BASE_DOES_NOT_EXIST,
                    "The account status notification handler configuration " +
                    "base " + DN_ACCT_NOTIFICATION_HANDLER_CONFIG_BASE +
                    " does not exist in the Directory Server configuration.  " +
                    "This entry must be present for the server to function " +
                    "properly");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "account status notification handler configuration:  " +
                    "%s.  It will be ignored");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_CANNOT_CREATE_HANDLER,
                    "An error occurred while attempting to create a " +
                    "Directory Server account status notification handler " +
                    "from the information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_ACCT_NOTIFICATION_HANDLER + " objectclass, which is " +
                    "required for account status notification handler " +
                    "definitions");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server account status notification " +
                    "handler.  If this is altered while the associated " +
                    "notification handler is enabled, then that handler must " +
                    "be disabled and re-enabled for the change to take " +
                    "effect");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " +
                    ATTR_ACCT_NOTIFICATION_HANDLER_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated account status notification handler");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_ACCT_NOTIFICATION_HANDLER_CLASS +
                    ":  %s");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid account status notification handler " +
                    "implementation:  %s");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server account status " +
                    "notification handler should be enabled.  Changes to " +
                    "this attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " +
                    ATTR_ACCT_NOTIFICATION_HANDLER_ENABLED +
                    " which indicates whether the account status " +
                    "notification handler should be enabled for use in the " +
                    "Directory Server");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_ACCT_NOTIFICATION_HANDLER_ENABLED +
                    ":  %s");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_CLASS_ACTION_REQUIRED,
                    "The requested change in the account status notification " +
                    "handler class name from %s to %s in configuration entry " +
                    "%s cannot be dynamically applied.  This change will not " +
                    "take effect until the notification handler is disabled " +
                    "and re-enabled or the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as an account status notification " +
                    "handler as defined in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_EXISTS,
                    "Unable to add a new account status notification handler " +
                    "entry with DN %s because there is already a " +
                    "notification handler registered with that DN");
    registerMessage(MSGID_CONFIG_ACCTNOTHANDLER_UNACCEPTABLE_CONFIG,
                    "The configuration for the account status notification " +
                    "handler defined in configuration entry %s was not " +
                    "acceptable according to its internal validation.  " +
                    "However, no specific information is available regarding " +
                    "the problem(s) with the entry");
    registerMessage(MSGID_CONFIG_CORE_DESCRIPTION_LOOKTHROUGH_LIMIT,
                    "Specifies the default maximum number of candidate " +
                    "entries checked for matches when processing a search " +
                    "operation.  This may be overridden on a per-user basis " +
                    "by including the " + OP_ATTR_USER_LOOKTHROUGH_LIMIT +
                    " operational attribute in the user's entry.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_CORE_INVALID_LOOKTHROUGH_LIMIT,
                    "Configuration entry %s has an invalid value for " +
                    "configuration attribute " + ATTR_LOOKTHROUGH_LIMIT +
                    " (It should be a positive integer value specifying " +
                    "the lookthrough limit to use, or a value of 0 or -1 to " +
                    "indicate that no limit should be enforced):  %s");
    registerMessage(MSGID_CONFIG_DESCRIPTION_BACKEND_MODE,
                    "The permissions used for the directory containing the " +
                    "backend database files");
    registerMessage(MSGID_CONFIG_BACKEND_MODE_INVALID,
                   "Configuration entry %s does not contain a valid value " +
                   "for configuration attribute " + ATTR_BACKEND_MODE +
                   " (It should be an UNIX permission mode in three-digit " +
                   "octal notation.)");
    registerMessage(MSGID_CONFIG_BACKEND_INSANE_MODE,
                   "Unable to set the requested file permissions to the " +
                   "backend database directory. The requested permissions " +
                   "will result in an inaccessable database");


    registerMessage(MSGID_CONFIG_GROUP_CANNOT_GET_BASE,
                    "An error occurred while attempting to retrieve the " +
                    "group implementation base entry " +
                    DN_GROUP_IMPLEMENTATION_CONFIG_BASE +
                    " from the Directory Server configuration:  %s");
    registerMessage(MSGID_CONFIG_GROUP_BASE_DOES_NOT_EXIST,
                    "The group implementation configuration base " +
                    DN_GROUP_IMPLEMENTATION_CONFIG_BASE + " does not exist " +
                    "in the Directory Server configuration.  This entry must " +
                    "be present for the server to function properly");
    registerMessage(MSGID_CONFIG_GROUP_ENTRY_UNACCEPTABLE,
                    "Configuration entry %s does not contain a valid " +
                    "group implementation configuration:  %s.  It will be " +
                    "ignored");
    registerMessage(MSGID_CONFIG_GROUP_CANNOT_CREATE_IMPLEMENTATION,
                    "An error occurred while attempting to create a " +
                    "Directory Server group implementation from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_GROUP_INVALID_OBJECTCLASS,
                    "Configuration entry %s does not contain the " +
                    OC_GROUP_IMPLEMENTATION + " objectclass, which is " +
                    "required for group implementation definitions");
    registerMessage(MSGID_CONFIG_GROUP_DESCRIPTION_CLASS_NAME,
                    "The fully-qualified name of the Java class that defines " +
                    "the Directory Server group implementation.  If this is " +
                    "while the associated implementation is enabled, then " +
                    "that group implementation must be disabled and " +
                    "re-enabled for the change to take effect");
    registerMessage(MSGID_CONFIG_GROUP_NO_CLASS_NAME,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " +
                    ATTR_GROUP_IMPLEMENTATION_CLASS +
                    " which specifies the fully-qualified class name for " +
                    "the associated group implementation");
    registerMessage(MSGID_CONFIG_GROUP_INVALID_CLASS_NAME,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_GROUP_IMPLEMENTATION_CLASS + ":  %s");
    registerMessage(MSGID_CONFIG_GROUP_INVALID_CLASS,
                    "Class %s specified in configuration entry %s does not " +
                    "contain a valid group implementation:  %s");
    registerMessage(MSGID_CONFIG_GROUP_DESCRIPTION_ENABLED,
                    "Indicates whether this Directory Server group " +
                    "implementation should be enabled.  Changes to this " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_CONFIG_GROUP_NO_ENABLED_ATTR,
                    "Configuration entry %s does not contain a valid value " +
                    "for configuration attribute " +
                    ATTR_GROUP_IMPLEMENTATION_ENABLED +
                    " which indicates whether the group implementation " +
                    "should be enabled for use in the Directory Server");
    registerMessage(MSGID_CONFIG_GROUP_INVALID_ENABLED_VALUE,
                    "Configuration entry %s has an invalid value for " +
                    "attribute " + ATTR_GROUP_IMPLEMENTATION_ENABLED +
                    ":  %s");
    registerMessage(MSGID_CONFIG_GROUP_CLASS_ACTION_REQUIRED,
                    "The requested change in the group implementation class " +
                    "name from %s to %s in configuration entry %s cannot be " +
                    "dynamically applied.  This change will not take effect " +
                    "until the group implementation is disabled and " +
                    "re-enabled or the Directory Server is restarted");
    registerMessage(MSGID_CONFIG_GROUP_INITIALIZATION_FAILED,
                    "An error occurred while trying to initialize an " +
                    "instance of class %s as a group implementation as " +
                    "in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_GROUP_EXISTS,
                    "Unable to add a new group implementation entry with DN " +
                    "%s because there is already a group implementation " +
                    "registered with that DN");
    registerMessage(MSGID_CONFIG_GROUP_UNACCEPTABLE_CONFIG,
                    "The configuration for the group implementation defined " +
                    "in configuration entry %s was not acceptable according " +
                    "to its internal validation.  However, no specific " +
                    "information is available regarding the problem(s) with " +
                    "the entry");


    registerMessage(
        MSGID_CONFIG_CORE_DESCRIPTION_REJECT_UNAUTHENTICATED_REQUESTS,
                    "Indicates whether the Directory Server should reject  " +
                    "requests from unauthenticated clients. If this is set " +
                    "to \"true\", then unauthenticated clients will only be "+
                    "allowed to send bind and StartTLS requests. Changes to "+
                    "this configuration attribute will take effect " +
                    "immediately");
    registerMessage(MSGID_CONFIG_CORE_REJECT_UNAUTHENTICATED_REQUESTS_INVALID,
                    "Configuration entry %s has an invalid value for" +
                    "configuration attribute " +
                    ATTR_REJECT_UNAUTHENTICATED_REQ + "(the value should " +
                    "be either true or false)");


    registerMessage(MSGID_CONFIG_CHANGE_NO_RESULT,
                    "%s.%s returned a result of null for entry %s");
    registerMessage(MSGID_CONFIG_CHANGE_RESULT_ERROR,
                    "%s.%s failed for entry %s:  result code=%s, admin " +
                    "action required=%b, messages=\"%s\"");
    registerMessage(MSGID_CONFIG_CHANGE_RESULT_ACTION_REQUIRED,
                    "%s.%s indicated that administrative action is required " +
                    "for entry %s:  messages=\"%s\"");
    registerMessage(MSGID_CONFIG_CHANGE_RESULT_MESSAGES,
                    "%s.%s succeeded but generated the following messages " +
                    "for entry %s:  %s");


    registerMessage(MSGID_CONFIG_VATTR_INVALID_SEARCH_FILTER,
                    "Unable to parse value \"%s\" from config entry \"%s\" " +
                    "as a valid search filter:  %s");
    registerMessage(MSGID_CONFIG_VATTR_SV_TYPE_WITH_MV_PROVIDER,
                    "The virtual attribute configuration in entry \"%s\" is " +
                    "not valid because attribute type %s is single-valued " +
                    "but provider %s may generate multiple values");
    registerMessage(MSGID_CONFIG_VATTR_SV_TYPE_WITH_MERGE_VALUES,
                    "The virtual attribute configuration in entry \"%s\" is " +
                    "not valid because attribute type %s is single-valued " +
                    "but the conflict behavior is configured to merge real " +
                    "and virtual values");
    registerMessage(MSGID_CONFIG_VATTR_INITIALIZATION_FAILED,
                    "An error occurred while trying to load an instance " +
                    "of class %s referenced in configuration entry %s as a " +
                    "virtual attribute provider:  %s");
    registerMessage(MSGID_CONFIG_ROTATION_POLICY_INVALID_CLASS,
                    "Class %s specified in attribute " + ATTR_LOGGER_CLASS +
                    " of configuration entry %s cannot be instantiated as " +
                    "a Directory Server log rotation policy:  %s");
    registerMessage(MSGID_CONFIG_RETENTION_POLICY_INVALID_CLASS,
                    "Class %s specified in attribute " + ATTR_LOGGER_CLASS +
                    " of configuration entry %s cannot be instantiated as " +
                    "a Directory Server log retention policy:  %s");
    registerMessage(MSGID_CONFIG_ROTATION_POLICY_CANNOT_CREATE_POLICY,
                    "An error occurred while attempting to create a " +
                    "Directory Server log rotation policy from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_RETENTION_POLICY_CANNOT_CREATE_POLICY,
                    "An error occurred while attempting to create a " +
                    "Directory Server log retention policy from the " +
                    "information in configuration entry %s:  %s");
    registerMessage(MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER,
                    "An error occurred while attempting create a text writer " +
                    "for a Directory Server logger from the information " +
                    "in configuration entry %s:  %s");
  }
}

