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
 * messages associated with Directory Server extensions like password storage
 * schemes, password validators, extended operations, SASL mechanisms, etc.
 */
public class ExtensionsMessages
{
  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize a message digest.  This takes two arguments, which
   * are the name of the message digest and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to base64-decode a value.  This takes two arguments, which
   * are the string to be base64-decoded and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 2;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * obtain the clear-text password from a password storage scheme that is not
   * reversible.  This takes a single argument, which is the name of the
   * password storage scheme.
   */
  public static final int MSGID_PWSCHEME_NOT_REVERSIBLE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 3;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to register the JMX alert handler MBean with the MBean server.
   * This takes a single argument, which is a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_JMX_ALERT_HANDLER_CANNOT_REGISTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 4;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to encode a password value.  This takes two arguments, which are
   * the fully-qualified name of the class implementing the storage scheme, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PWSCHEME_CANNOT_ENCODE_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 5;



  /**
   * The message ID for the message that will be used as the description of the
   * FIFO-based entry cache max memory percentage configuration attribute.  This
   * does not take any arguments.
   */
  public static final int MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 6;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the percentage of memory to use for the FIFO-based
   * entry cache.  This takes three arguments, which are the DN of the
   * configuration entry, a string representation of the exception that was
   * caught, and the default percentage value that will be used.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_MEMORY_PCT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 7;



  /**
   * The message ID for the message that will be used as the description of the
   * FIFO-based entry cache max memory percentage configuration attribute.  This
   * does not take any arguments.
   */
  public static final int MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 8;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the maximum number of entries to hold in the FIFO-based
   * entry cache.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 9;



  /**
   * The message ID for the message that will be used as the description of the
   * FIFO-based entry cache lock timeout configuration attribute.  This does not
   * take any arguments.
   */
  public static final int MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 10;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the lock timeout to use for the FIFO-based entry cache.
   * This takes three arguments, which are the DN of the configuration entry, a
   * string representation of the exception that was caught, and the default
   * lock timeout that will be used.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 11;



  /**
   * The message ID for the message that will be used as the description of the
   * FIFO-based entry cache include filters configuration attribute.  This does
   * not take any arguments.
   */
  public static final int MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 12;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an include filter.  This takes three arguments, which are
   * the invalid filter string, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DECODE_INCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 13;



  /**
   * The message ID for the message that will be used if none of the include
   * filter strings can be parsed.  This takes a single argument, which is the
   * DN of the configuration entry.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 14;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the include filters to use for the FIFO-based entry
   * cache.  This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 15;



  /**
   * The message ID for the message that will be used as the description of the
   * FIFO-based entry cache exclude filters configuration attribute.  This does
   * not take any arguments.
   */
  public static final int MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 16;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an exclude filter.  This takes three arguments, which are
   * the invalid filter string, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DECODE_EXCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 17;



  /**
   * The message ID for the message that will be used if none of the exclude
   * filter strings can be parsed.  This takes a single argument, which is the
   * DN of the configuration entry.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 18;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the exclude filters to use for the FIFO-based entry
   * cache.  This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_FIFOCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 19;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid max memory percentage.  This takes two arguments, which are the DN
   * of the configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_MAX_MEMORY_PCT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 20;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid max entries.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_MAX_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 21;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid lock timeout.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 22;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid include filter.  This takes three arguments, which are the DN
   * of the configuration entry, the invalid filter string, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 23;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid set of include filters.  This takes two arguments, which are the DN
   * of the configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 24;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid exclude filter.  This takes three arguments, which are the DN
   * of the configuration entry, the invalid filter string, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 25;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid set of exclude filters.  This takes two arguments, which are the DN
   * of the configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 26;



  /**
   * The message ID for the message that will be used if entry cache memory
   * percent has been updated.  This takes two arguments, which are the new
   * percentage of memory that may be used and the corresponding size in bytes.
   */
  public static final int MSGID_FIFOCACHE_UPDATED_MAX_MEMORY_PCT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 27;



  /**
   * The message ID for the message that will be used if maximum number of cache
   * entries has been updated.  This takes a single argument, which is the new
   * maximum number of entries.
   */
  public static final int MSGID_FIFOCACHE_UPDATED_MAX_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 28;



  /**
   * The message ID for the message that will be used if the cache lock timeout
   * has been updated.  This takes a single argument, which is the new lock
   * timeout.
   */
  public static final int MSGID_FIFOCACHE_UPDATED_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 29;



  /**
   * The message ID for the message that will be used if the cache include
   * filter set has been updated.  This does not take any arguments.
   */
  public static final int MSGID_FIFOCACHE_UPDATED_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 30;



  /**
   * The message ID for the message that will be used if the cache exclude
   * filter set has been updated.  This does not take any arguments.
   */
  public static final int MSGID_FIFOCACHE_UPDATED_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 31;



  /**
   * The message ID for the message that will be used if a password modify
   * extended request sequence contains an element with an invalid type.  This
   * takes a single argument, which is a string representation of that type.
   */
  public static final int MSGID_EXTOP_PASSMOD_ILLEGAL_REQUEST_ELEMENT_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 32;



  /**
   * The message ID for the message that will be used if a password modify
   * extended request sequence cannot be decoded for some reason.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 33;



  /**
   * The message ID for the message that will be used if a password modify
   * extended request sequence cannot be processed because it did not contain
   * an authorization ID and the underlying connection is not authenticated.
   * This does not take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_NO_AUTH_OR_USERID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 34;



  /**
   * The message ID for the message that will be used if a password modify
   * extended request sequence cannot be processed because the server could not
   * obtain a write lock on the user's entry.  This takes a single argument,
   * which is the DN of the user's entry.
   */
  public static final int MSGID_EXTOP_PASSMOD_CANNOT_LOCK_USER_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 35;



  /**
   * The message ID for the message that will be used if the DN used as the
   * authorization ID for the password modify operation cannot be decoded.
   * This takes a single argument, which is the value provided for the
   * authorization ID.
   */
  public static final int MSGID_EXTOP_PASSMOD_CANNOT_DECODE_AUTHZ_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 36;



  /**
   * The message ID for the message that will be used if the DN used as the
   * authorization ID for the password modify operation cannot be decoded.
   * This takes a single argument, which is the value provided for the
   * authorization ID.
   */
  public static final int MSGID_EXTOP_PASSMOD_INVALID_AUTHZID_STRING =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 37;



  /**
   * The message ID for the message that will be used if it is not possible to
   * retrieve the entry targeted by DN in a password modify operation.  This
   * takes a single argument, which is the target DN.
   */
  public static final int MSGID_EXTOP_PASSMOD_NO_USER_ENTRY_BY_AUTHZID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 38;



  /**
   * The message ID for the message that will be used if it is not possible to
   * retrieve the entry targeted by UID in a password modify operation.  This
   * takes a single argument, which is the target UID.
   */
  public static final int MSGID_EXTOP_PASSMOD_NO_DN_BY_AUTHZID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 39;



  /**
   * The message ID for the message that will be used if multiple entries
   * matched the provided authorization ID.  This takes a single argument, which
   * is the target UID.
   */
  public static final int MSGID_EXTOP_PASSMOD_MULTIPLE_ENTRIES_BY_AUTHZID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 40;



  /**
   * The message ID for the message that will be used if the old password
   * provided in a password modify request is invalid.  This does not take any
   * arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_INVALID_OLD_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 41;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * access a key manager without having one defined in the configuration.
   */
  public static final int MSGID_NULL_KEYMANAGER_NO_MANAGER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 42;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the location of the key manager file.
   * This does not take any arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_DESCRIPTION_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 43;



  /**
   * The message ID for the message that will be used if no file is specified
   * for a file-based keystore.  This takes a single argument, which is the DN
   * of the configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_NO_FILE_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 44;



  /**
   * The message ID for the message that will be used if the file specified for
   * a file-based keystore does not exist.  This takes two arguments, which are
   * the specified path to the file and the DN of the configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_NO_SUCH_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 45;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the path to the keystore file.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 46;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the keystore type.  This does not take
   * any arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_DESCRIPTION_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 47;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the keystore type.  This takes two arguments, which are
   * the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 48;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the Java property containing the
   * keystore PIN.  This does not take any arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_PROPERTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 49;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in a Java property but that property is not set.  This takes
   * two arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 50;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the Java property containing the keystore PIN.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 51;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the environment variable containing the
   * keystore PIN.  This does not take any arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_ENVAR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 52;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in an environment variable but that variable is not set.  This
   * takes two arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 53;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the environment variable containing the keystore PIN.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 54;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the file containing the keystore PIN.
   * This does not take any arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 55;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in a file but that file does not exist.  This takes two
   * arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_PIN_NO_SUCH_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 56;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read the keystore PIN file.  This takes three arguments,
   * which are the path to the PIN file, the DN of the configuration entry, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 57;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in a file but that file is empty.  This takes two arguments,
   * which are the name of that property and the DN of the configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_PIN_FILE_EMPTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 58;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the path to the file containing the keystore PIN.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 59;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the keystore PIN. This does not take any
   * arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 60;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the keystore PIN.  This takes two arguments, which are
   * the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 60;



  /**
   * The message ID for the message that will be used if the key manager
   * configuration entry does not provide a means of obtaining the PIN.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_FILE_KEYMANAGER_NO_PIN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 61;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load the keystore.  This takes two arguments, which are the
   * path to the keystore file and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_LOAD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 62;



  /**
   * The message ID for the message that will be used if an invalid keystore
   * type is specified.  This takes three arguments, which are the provided
   * keystore type, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_INVALID_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if the path to the
   * keystore file is updated.  This takes two arguments, which are the DN of
   * the configuration entry and the new path to the keystore file.
   */
  public static final int MSGID_FILE_KEYMANAGER_UPDATED_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 64;



  /**
   * The message ID for the message that will be used if the keystore type is
   * updated.  This takes two arguments, which are the DN of the configuration
   * entry and the new keystore type.
   */
  public static final int MSGID_FILE_KEYMANAGER_UPDATED_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 65;



  /**
   * The message ID for the message that will be used if the PIN used to access
   * the keystore is updated.  This does not take any arguments.
   */
  public static final int MSGID_FILE_KEYMANAGER_UPDATED_PIN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 66;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the Java property containing the
   * keystore PIN.  This does not take any arguments.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_PROPERTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 67;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in a Java property but that property is not set.  This takes
   * two arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 68;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the Java property containing the keystore PIN.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY =
            CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 69;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the environment variable containing the
   * keystore PIN.  This does not take any arguments.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ENVAR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 70;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in an environment variable but that variable is not set.  This
   * takes two arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 71;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the environment variable containing the keystore PIN.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 72;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the file containing the keystore PIN.
   * This does not take any arguments.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 73;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in a file but that file does not exist.  This takes two
   * arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 74;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read the keystore PIN file.  This takes three arguments,
   * which are the path to the PIN file, the DN of the configuration entry, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 75;



  /**
   * The message ID for the message that will be used if the keystore PIN should
   * be specified in a file but that file is empty.  This takes two arguments,
   * which are the name of that property and the DN of the configuration entry.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_PIN_FILE_EMPTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 76;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the path to the file containing the keystore PIN.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 77;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the keystore PIN. This does not take any
   * arguments.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 78;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the keystore PIN.  This takes two arguments, which are
   * the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int
       MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR =
            CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 79;



  /**
   * The message ID for the message that will be used if the key manager
   * configuration entry does not provide a means of obtaining the PIN.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_NO_PIN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 80;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load the keystore.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_CANNOT_LOAD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 81;



  /**
   * The message ID for the message that will be used if the PIN used to access
   * the keystore is updated.  This does not take any arguments.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_UPDATED_PIN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 82;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting create a trust factory for the file-based trust store.  This
   * takes two arguments, which are the name of the trust store file and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 83;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting create a trust factory for the PKCS#11 trust store.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_PKCS11_KEYMANAGER_CANNOT_CREATE_FACTORY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 84;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the location of the trust manager file.
   * This does not take any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_DESCRIPTION_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 85;



  /**
   * The message ID for the message that will be used if no file is specified
   * for a file-based trust store.  This takes a single argument, which is the
   * DN of the configuration entry.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_NO_FILE_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 86;



  /**
   * The message ID for the message that will be used if the file specified for
   * a file-based trust store does not exist.  This takes two arguments, which
   * are the specified path to the file and the DN of the configuration entry.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 87;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the path to the trust store file.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 88;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the trust store type.  This does not
   * take any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_DESCRIPTION_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 89;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the trust store type.  This takes two arguments, which
   * are the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 90;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the Java property containing the
   * trust store PIN.  This does not take any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_PROPERTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 91;



  /**
   * The message ID for the message that will be used if the trust store PIN
   * should be specified in a Java property but that property is not set.  This
   * takes two arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 92;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the Java property containing the trust store PIN.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_PROPERTY =
            CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 93;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the environment variable containing the
   * trust store PIN.  This does not take any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ENVAR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 94;



  /**
   * The message ID for the message that will be used if the trust store PIN
   * should be specified in an environment variable but that variable is not
   * set.  This takes two arguments, which are the name of that property and the
   * DN of the configuration entry.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 95;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the environment variable containing the trust store
   * PIN.  This takes two arguments, which are the DN of the configuration entry
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_ENVAR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 96;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the file containing the trust store PIN.
   * This does not take any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 97;



  /**
   * The message ID for the message that will be used if the trust store PIN
   * should be specified in a file but that file does not exist.  This takes two
   * arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 98;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read the trust store PIN file.  This takes three arguments,
   * which are the path to the PIN file, the DN of the configuration entry, and
   * a string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 99;



  /**
   * The message ID for the message that will be used if the trust store PIN
   * should be specified in a file but that file is empty.  This takes two
   * arguments, which are the name of that property and the DN of the
   * configuration entry.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 100;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the path to the file containing the trust store PIN.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 101;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the trust store PIN. This does not take
   * any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 102;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the trust store PIN.  This takes two arguments, which
   * are the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int
       MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR =
            CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 103;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load the trust store.  This takes two arguments, which are
   * the path to the trust store file and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_CANNOT_LOAD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 104;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting create a trust factory for the file-based trust store.  This
   * takes two arguments, which are the name of the trust store file and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 105;



  /**
   * The message ID for the message that will be used if an invalid trust store
   * type is specified.  This takes three arguments, which are the provided
   * trust store type, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_INVALID_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 106;



  /**
   * The message ID for the message that will be used if the path to the
   * trust store file is updated.  This takes two arguments, which are the DN of
   * the configuration entry and the new path to the trust store file.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_UPDATED_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 107;



  /**
   * The message ID for the message that will be used if the trust store type is
   * updated.  This takes two arguments, which are the DN of the configuration
   * entry and the new trust store type.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_UPDATED_TYPE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 108;



  /**
   * The message ID for the message that will be used if the PIN used to access
   * the trust store is updated.  This does not take any arguments.
   */
  public static final int MSGID_FILE_TRUSTMANAGER_UPDATED_PIN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 109;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while attempting to read data from a client using the null security
   * provider.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_NULL_SECURITY_PROVIDER_READ_ERROR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 110;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while attempting to write data to a client using the null security
   * provider.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_NULL_SECURITY_PROVIDER_WRITE_ERROR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 111;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the SSL context for the TLS connection security
   * provider.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_TLS_SECURITY_PROVIDER_CANNOT_INITIALIZE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 112;



  /**
   * The message ID for the message that will be used if an unexpected result is
   * returned to the TLS connection security provider when trying to unwrap SSL
   * data read from the client.  This takes a single argument, which is a string
   * representation of the unwrap result that was returned.
   */
  public static final int MSGID_TLS_SECURITY_PROVIDER_UNEXPECTED_UNWRAP_STATUS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 113;



  /**
   * The message ID for the message that will be used if an unexpected error is
   * encountered while attempting to read data from the client using the TLS
   * connection security provider.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_TLS_SECURITY_PROVIDER_READ_ERROR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 114;



  /**
   * The message ID for the message that will be used if an attempt to write
   * data to the client cannot be processed because outstanding SSL negotiation
   * needs to read data from the client and there is none available.  This does
   * not take any arguments.
   */
  public static final int MSGID_TLS_SECURITY_PROVIDER_WRITE_NEEDS_UNWRAP =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 115;



  /**
   * The message ID for the message that will be used if an unexpected result is
   * returned to the TLS connection security provider when trying to wrap data
   * to write to the client.  This takes a single argument, which is a string
   * representation of the wrap result that was returned.
   */
  public static final int MSGID_TLS_SECURITY_PROVIDER_UNEXPECTED_WRAP_STATUS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 116;



  /**
   * The message ID for the message that will be used if an unexpected error is
   * encountered while attempting to write data to the client using the TLS
   * connection security provider.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_TLS_SECURITY_PROVIDER_WRITE_ERROR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 117;



  /**
   * The message ID for the message that will be used if the subject equals DN
   * certificate mapper is asked to map a null or empty certificate chain.  This
   * does not take any arguments.
   */
  public static final int MSGID_SEDCM_NO_PEER_CERTIFICATE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 118;



  /**
   * The message ID for the message that will be used if the subject equals DN
   * certificate mapper is asked to map a certificate chain in which the peer
   * certificate is not an X.509 certificate.  This takes a single argument,
   * which is the certificate type for the peer certificate.
   */
  public static final int MSGID_SEDCM_PEER_CERT_NOT_X509 =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 119;



  /**
   * The message ID for the message that will be used if an error occurs while
   * the subject equals DN certificate mapper is trying to decode the peer
   * certificate's subject as a DN.  This takes two arguments, which are a
   * string representation of the peer's subject and a message that explains the
   * reason it could not be decoded.
   */
  public static final int MSGID_SEDCM_CANNOT_DECODE_SUBJECT_AS_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 120;



  /**
   * The message ID for the message that will be used if an error occurs while
   * the subject equals DN certificate mapper is trying to retrieve the entry
   * with a DN equal to the subject DN.  This takes two arguments, which are a
   * string representation of the peer's subject DN and a message that explains
   * the reason the entry could not be retrieved.
   */
  public static final int MSGID_SEDCM_CANNOT_GET_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 121;



  /**
   * The message ID for the message that will be used if the subject equals DN
   * certificate mapper is asked to map a certificate when no user exists in the
   * directory with a DN equal to the certificate subject.  This takes a single
   * argument, which is the subject DN from the peer certificate.
   */
  public static final int MSGID_SEDCM_NO_USER_FOR_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 122;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a SASL EXTERNAL bind when no client connection is available.  This
   * does not take any arguments.
   */
  public static final int MSGID_SASLEXTERNAL_NO_CLIENT_CONNECTION =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 123;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a SASL EXTERNAL bind using a client connection that does not have a
   * security provider.  This does not take any arguments.
   */
  public static final int MSGID_SASLEXTERNAL_NO_SECURITY_PROVIDER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 124;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a SASL EXTERNAL bind on a client connection that is using a
   * security provider other than the TLS security provider.  This takes a
   * single argument, which is the name of the security provider in use.
   */
  public static final int MSGID_SASLEXTERNAL_CLIENT_NOT_USING_TLS_PROVIDER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 125;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a SASL EXTERNAL bind over a client connection that did not present
   * a certificate chain.  This does not take any arguments.
   */
  public static final int MSGID_SASLEXTERNAL_NO_CLIENT_CERT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 126;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a SASL EXTERNAL bind but no mapping could be established between
   * the client certificate chain and a user in the directory.  This does not
   * take any arguments.
   */
  public static final int MSGID_SASLEXTERNAL_NO_MAPPING =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 127;



  /**
   * The message ID for the message that will be used if a client attempts to
   * use the StartTLS extended operation but the client connection is not
   * available.  This does not take any arguments.
   */
  public static final int MSGID_STARTTLS_NO_CLIENT_CONNECTION =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 128;



  /**
   * The message ID for the message that will be used if a client attempts to
   * use the StartTLS extended operation but the type of client connection used
   * does not allow StartTLS.  This does not take any arguments.
   */
  public static final int MSGID_STARTTLS_NOT_TLS_CAPABLE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 129;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to enable StartTLS on a client connection.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_STARTTLS_ERROR_ON_ENABLE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 130;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to configure client certificate validation.  This does not
   * take any arguments.
   */
  public static final int MSGID_SASLEXTERNAL_DESCRIPTION_VALIDATION_POLICY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 131;



  /**
   * The message ID for the message that will be used if the attribute used to
   * configure client certificate validation policy has an invalid value.  This
   * takes two arguments, which are the DN of the configuration entry and the
   * invalid value.
   */
  public static final int MSGID_SASLEXTERNAL_INVALID_VALIDATION_VALUE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 132;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the client certificate validation policy.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLEXTERNAL_CANNOT_GET_VALIDATION_POLICY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 133;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the attribute holding certificates in user
   * entries.  This does not take any arguments.
   */
  public static final int MSGID_SASLEXTERNAL_DESCRIPTION_CERTIFICATE_ATTRIBUTE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 134;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the attribute to use for certificate validation.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLEXTERNAL_CANNOT_GET_CERT_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 135;



  /**
   * The message ID for the message that will be used if the certificate
   * attribute is not defined in the server schema.  This takes two arguments,
   * which are the name of the certificate attribute and the DN of the
   * configuration entry.
   */
  public static final int MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 136;



  /**
   * The message ID for the message that will be used if certificate validation
   * is required but the user entry does not contain any certificates.  This
   * takes a single argument, which is the DN of the user's entry.
   */
  public static final int MSGID_SASLEXTERNAL_NO_CERT_IN_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 137;



  /**
   * The message ID for the message that will be used if certificate validation
   * is required but the user entry does not contain a certificate that matches
   * the peer certificate.  This takes a single argument, which is the DN of the
   * user's entry.
   */
  public static final int MSGID_SASLEXTERNAL_PEER_CERT_NOT_FOUND =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 138;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to validate the peer certificate against a certificate in the
   * user's entry.  This takes two arguments, which are the DN of the user's
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_SASLEXTERNAL_CANNOT_VALIDATE_CERT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 139;



  /**
   * The message ID for the message that will be used to indicate that the peer
   * certificate validation policy has been updated.  This takes two arguments,
   * which are the DN of the configuration entry and the new validation policy.
   */
  public static final int MSGID_SASLEXTERNAL_UPDATED_VALIDATION_POLICY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 140;



  /**
   * The message ID for the message that will be used to indicate that the
   * certificate attribute for peer validation has been updated.  This takes two
   * arguments, which are the DN of the configuration entry and the new
   * attribute type name or OID.
   */
  public static final int MSGID_SASLEXTERNAL_UPDATED_CERT_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 141;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the username attribute used to locate entries
   * when authenticating via SASL PLAIN.  This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_DESCRIPTION_USERNAME_ATTRIBUTE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 142;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the attribute to use for username lookups.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_GET_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 143;



  /**
   * The message ID for the message that will be used if the username attribute
   * is not defined in the server schema.  This takes two arguments, which are
   * the name of the username attribute and the DN of the configuration entry.
   */
  public static final int MSGID_SASLPLAIN_UNKNOWN_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 144;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the user base DN used to locate entries when
   * authenticating via SASL PLAIN.  This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_DESCRIPTION_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 145;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the base DN to use for username lookups.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_GET_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 146;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request does not include any SASL credentials.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLPLAIN_NO_SASL_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 147;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request does not include null characters in the SASL credentials.  This
   * does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_NO_NULLS_IN_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 148;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request does not include a second null character to separate the authcID
   * from the password.  This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_NO_SECOND_NULL =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 149;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request included a zero-length authcID.  This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_ZERO_LENGTH_AUTHCID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 150;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request included a zero-length password.  This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_ZERO_LENGTH_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 151;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request included an authentication ID that started with "dn:" but the rest
   * of the value couldn't be decoded as a DN.  This takes two arguments, which
   * are the provided authentication ID and a message with information about the
   * DN decoding failure.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_DECODE_AUTHCID_AS_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 152;



  /**
   * The message ID for the message that will be used if a SASL PLAIN bind
   * request included an authentication ID of "dn:" (i.e., a zero-length DN).
   * This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_AUTHCID_IS_NULL_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 153;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve an entry by DN for a SASL PLAIN bind request.  This
   * takes two arguments, which are the DN of the entry and a message with
   * information about the reason it could not be retrieved.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_GET_ENTRY_BY_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 154;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to perform an internal search to resolve an authentication ID to
   * a user entry.  This takes three arguments, which are the authentication ID,
   * a string representation of the result code, and the error message from the
   * search operation.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_PERFORM_INTERNAL_SEARCH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 155;



  /**
   * The message ID for the message that will be used if an internal search
   * to resolve the provided username matches multiple entries.  This takes a
   * single argument, which is the authentication ID.
   */
  public static final int MSGID_SASLPLAIN_MULTIPLE_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 156;



  /**
   * The message ID for the message that will be used if an internal search
   * to resolve the provided username does not match any entries.  This takes a
   * single argument, which is the authentication ID.
   */
  public static final int MSGID_SASLPLAIN_NO_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 157;



  /**
   * The message ID for the message that will be used if a user entry does not
   * contain a password attribute.  This takes a single argument, which is the
   * name or OID of the password attribute.
   */
  public static final int MSGID_SASLPLAIN_NO_PW_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 158;



  /**
   * The message ID for the message that will be used if a user entry has a
   * password with an unrecognized storage scheme.  This takes two arguments,
   * which are the DN of the user and the name of the unknown storage scheme.
   */
  public static final int MSGID_SASLPLAIN_UNKNOWN_STORAGE_SCHEME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 159;



  /**
   * The message ID for the message that will be used if SASL PLAIN
   * authentication fails because the user provided an invalid password.
   */
  public static final int MSGID_SASLPLAIN_INVALID_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 160;



  /**
   * The message ID for the message that will be used to indicate that the
   * username attribute for authcID/authzID lookups has been updated.  This
   * takes two arguments, which are the DN of the configuration entry and the
   * new attribute type name or OID.
   */
  public static final int MSGID_SASLPLAIN_UPDATED_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 161;



  /**
   * The message ID for the message that will be used to indicate that the user
   * base DN for authcID/authzID lookups has been updated.  This takes two
   * arguments, which are the DN of the configuration entry and the new base DN.
   */
  public static final int MSGID_SASLPLAIN_UPDATED_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 162;



  /**
   * The message ID for the message that will be used if it is not possible to
   * obtain a read lock on an entry targeted by a SASL PLAIN bind request.  This
   * takes a single argument, which is the DN of the target entry.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 163;



  /**
   * The message ID for the message that will be used if it is not possible to
   * obtain a read lock on an entry with a DN equal to the certificate subject.
   * This takes a single argument, which is the DN of the target entry.
   */
  public static final int MSGID_SEDCM_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 164;



  /**
   * The message ID for the message that will be written to the error log if a
   * client binds using SASL ANONYMOUS and provides trace information in the
   * request.  This takes three arguments, which are the connection ID for the
   * client connection, the operation ID for the bind request, and the trace
   * information from the request.
   */
  public static final int MSGID_SASLANONYMOUS_TRACE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 165;



  /**
   * The message ID for the message that will be used if it is not possible to
   * obtain a message digest for generating MD5 hashes.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_GET_MESSAGE_DIGEST =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 166;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the username attribute used to locate entries
   * when authenticating via SASL CRAM-MD5.  This does not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_DESCRIPTION_USERNAME_ATTRIBUTE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 167;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the attribute to use for username lookups.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_GET_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 168;



  /**
   * The message ID for the message that will be used if the username attribute
   * is not defined in the server schema.  This takes two arguments, which are
   * the name of the username attribute and the DN of the configuration entry.
   */
  public static final int MSGID_SASLCRAMMD5_UNKNOWN_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 169;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the user base DN used to locate entries when
   * authenticating via SASL CRAM-MD5.  This does not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_DESCRIPTION_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 170;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the base DN to use for username lookups.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_GET_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 171;



  /**
   * The message ID for the message that will be used if SASL CRAM-MD5
   * authentication fails because there is no stored challenge to use in
   * processing a second-stage request.  This does not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_NO_STORED_CHALLENGE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 172;



  /**
   * The message ID for the message that will be used if SASL CRAM-MD5
   * authentication fails because the stored challenge is an invalid type of
   * object.  This does not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_INVALID_STORED_CHALLENGE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 173;



  /**
   * The message ID for the message that will be used if SASL CRAM-MD5
   * authentication fails because the SASL credentials do not contain a space to
   * separate the username from the password digest.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLCRAMMD5_NO_SPACE_IN_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 174;



  /**
   * The message ID for the message that will be used if the digest in the SASL
   * CRAM-MD5 credentials has an invalid length.  This takes two arguments,
   * which are the length of the provided credentials and the expected length.
   */
  public static final int MSGID_SASLCRAMMD5_INVALID_DIGEST_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 175;



  /**
   * The message ID for the message that will be used if the digest in the SASL
   * CRAM-MD5 credentials is not comprised entirely of hex characters.  This
   * takes a single argument, which is an explanation of the problem found.
   */
  public static final int MSGID_SASLCRAMMD5_INVALID_DIGEST_CONTENT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 176;



  /**
   * The message ID for the message that will be used if a SASL CRAM-MD5 bind
   * request included a username that started with "dn:" but the rest of the
   * value couldn't be decoded as a DN.  This takes two arguments, which are the
   * are the provided username and a message with information about the DN
   * decoding failure.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_DECODE_USERNAME_AS_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 177;



  /**
   * The message ID for the message that will be used if a SASL CRAM-MD5 bind
   * request included a username of "dn:" (i.e., a zero-length DN).  This does
   * not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_USERNAME_IS_NULL_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 178;



  /**
   * The message ID for the message that will be used if it is not possible to
   * obtain a read lock on an entry targeted by a SASL CRAM-MD5 bind request.
   * This takes a single argument, which is the DN of the target entry.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 179;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve an entry by DN for a SASL CRAM-MD5 bind request.
   * This takes two arguments, which are the DN of the entry and a message with
   * information about the reason it could not be retrieved.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_GET_ENTRY_BY_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 180;



  /**
   * The message ID for the message that will be used if a SASL CRAM-MD5 bind
   * request included a zero-length username.  This does not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_ZERO_LENGTH_USERNAME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 181;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to perform an internal search to resolve a username to a user
   * entry.  This takes three arguments, which are the username, a string
   * representation of the result code, and the error message from the search
   * operation.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_PERFORM_INTERNAL_SEARCH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 182;



  /**
   * The message ID for the message that will be used if an internal search
   * to resolve the provided username matches multiple entries.  This takes a
   * single argument, which is the username.
   */
  public static final int MSGID_SASLCRAMMD5_MULTIPLE_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 183;



  /**
   * The message ID for the message that will be used if an internal search
   * to resolve the provided username does not match any entries.  This takes a
   * single argument, which is the username.
   */
  public static final int MSGID_SASLCRAMMD5_NO_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 184;



  /**
   * The message ID for the message that will be used if a user entry does not
   * contain a password attribute.  This takes a single argument, which is the
   * name or OID of the password attribute.
   */
  public static final int MSGID_SASLCRAMMD5_NO_PW_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 185;



  /**
   * The message ID for the message that will be used if a user entry has a
   * password with an unrecognized storage scheme.  This takes two arguments,
   * which are the DN of the user and the name of the unknown storage scheme.
   */
  public static final int MSGID_SASLCRAMMD5_UNKNOWN_STORAGE_SCHEME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 186;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to obtain the clear-text version of a password that is encoded with
   * what should be a reversible storage scheme.  This takes three arguments,
   * which are the DN of the user, the name of the storage scheme, and a message
   * explaining the problem.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_GET_CLEAR_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 187;



  /**
   * The message ID for the message that will be used if SASL CRAM-MD5
   * authentication fails because the user provided an invalid password.
   */
  public static final int MSGID_SASLCRAMMD5_INVALID_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 188;



  /**
   * The message ID for the message that will be used if SASL CRAM-MD5
   * authentication fails because the user entry does not have any passwords
   * stored in a reversible form.  This takes a single argument, which is the
   * DN of the user.
   */
  public static final int MSGID_SASLCRAMMD5_NO_REVERSIBLE_PASSWORDS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 189;



  /**
   * The message ID for the message that will be used to indicate that the
   * attribute for username lookups has been updated.  This takes two arguments,
   * which are the DN of the configuration entry and the new attribute type name
   * or OID.
   */
  public static final int MSGID_SASLCRAMMD5_UPDATED_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 190;



  /**
   * The message ID for the message that will be used to indicate that the base
   * DN for username lookups has been updated.  This takes two arguments, which
   * are the DN of the configuration entry and the new base DN.
   */
  public static final int MSGID_SASLCRAMMD5_UPDATED_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 191;



  /**
   * The message ID for the message that will be used if it is not possible to
   * obtain a message digest for generating MD5 hashes.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_MESSAGE_DIGEST =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 192;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the username attribute used to locate entries
   * when authenticating via SASL DIGEST-MD5.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_DESCRIPTION_USERNAME_ATTRIBUTE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 193;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the attribute to use for username lookups.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 194;



  /**
   * The message ID for the message that will be used if the username attribute
   * is not defined in the server schema.  This takes two arguments, which are
   * the name of the username attribute and the DN of the configuration entry.
   */
  public static final int MSGID_SASLDIGESTMD5_UNKNOWN_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 195;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the user base DN used to locate entries when
   * authenticating via SASL DIGEST-MD5.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_DESCRIPTION_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 196;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the base DN to use for username lookups.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 197;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the realm for DIGEST-MD5 authentication.  This
   * does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_DESCRIPTION_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 198;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the realm to advertise for DIGEST-MD5 authentication.
   * This takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 199;



  /**
   * The message ID for the message that will be used if the initial challenge
   * generated by the server is not less than 2048 bytes.  This takes a single
   * argument, which is the size of the generated challenge.
   */
  public static final int MSGID_SASLDIGESTMD5_CHALLENGE_TOO_LONG =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 200;



  /**
   * The message ID for the message that will be used if the client provides
   * SASL credentials but the server doesn't have any previous SASL state for
   * that client.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_STORED_STATE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 201;



  /**
   * The message ID for the message that will be used if the client provides
   * SASL credentials but the stored SASL state information isn't appropriate
   * for DIGEST-MD5 authentication.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_STORED_STATE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 202;



  /**
   * The message ID for the message that will be used if the client credentials
   * cannot be parsed as a string using the ISO-8859-1 character set.  This
   * takes two arguments, which are the name of the character set used and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_PARSE_ISO_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 203;



  /**
   * The message ID for the message that will be used if the client credentials
   * cannot be parsed as a string using the UTF-8 character set.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_PARSE_UTF8_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 204;



  /**
   * The message ID for the message that will be used if the client credentials
   * contain a token with no equal sign.  This takes two arguments, which are
   * the invalid token and the position at which it begins in the credential
   * string.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_TOKEN_IN_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 205;



  /**
   * The message ID for the message that will be used if the client credentials
   * specify a character set of anything other than UTF-8.  This takes a single
   * argument, which is the character set specified by the client.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_CHARSET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 206;



  /**
   * The message ID for the message that will be used if the realm provided by
   * the client is expected to be a DN but cannot be parsed as one.  This takes
   * two arguments, which are the provided realm value and a message explaining
   * the problem that was encountered.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_DECODE_REALM_AS_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 207;



  /**
   * The message ID for the message that will be used if the realm provided by
   * the client is not what was expected.  This takes a single argument, which
   * is the realm that was provided.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 208;



  /**
   * The message ID for the message that will be used if the nonce provided by
   * the client does not match the nonce supplied by the server.  This does not
   * take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_NONCE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 209;



  /**
   * The message ID for the message that will be used if the nonce count
   * provided by the client cannot be decoded as an integer.  This takes a
   * single argument, which is the provided nonce count.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_DECODE_NONCE_COUNT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 210;



  /**
   * The message ID for the message that will be used if the nonce count
   * stored by the server for the client connection cannot be decoded as an
   * integer.  This takes a single argument, which is a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_DECODE_STORED_NONCE_COUNT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 211;



  /**
   * The message ID for the message that will be used if the nonce count
   * provided by the client is different from what was expected.  This does not
   * take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_NONCE_COUNT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 212;



  /**
   * The message ID for the message that will be used if the client requests the
   * unsupported integrity QoP.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_INTEGRITY_NOT_SUPPORTED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 213;



  /**
   * The message ID for the message that will be used if the client requests the
   * unsupported confidentiality QoP.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_CONFIDENTIALITY_NOT_SUPPORTED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 214;



  /**
   * The message ID for the message that will be used if the client requested an
   * unsupported QoP mechanism.  This takes a single argument, which is the QoP
   * value requested by the client.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_QOP =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 215;



  /**
   * The message ID for the message that will be used if the digest included
   * in the client credentials could not be parsed.  This takes a single
   * argument, which is a string representation of the stack trace that was
   * caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_PARSE_RESPONSE_DIGEST =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 216;



  /**
   * The message ID for the message that will be used if client credentials
   * included an invalid token.  This takes a single argument, which is the
   * name of the invalid token.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_RESPONSE_TOKEN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 217;



  /**
   * The message ID for the message that will be used if client credentials
   * did not include the required "username" token.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_USERNAME_IN_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 218;



  /**
   * The message ID for the message that will be used if client credentials
   * did not include the required "nonce" token.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_NONCE_IN_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 219;



  /**
   * The message ID for the message that will be used if client credentials
   * did not include the required "cnonce" token.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_CNONCE_IN_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 220;



  /**
   * The message ID for the message that will be used if client credentials
   * did not include the required "nc" token.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_NONCE_COUNT_IN_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 221;



  /**
   * The message ID for the message that will be used if client credentials
   * did not include the required "digest-uri" token.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_DIGEST_URI_IN_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 222;



  /**
   * The message ID for the message that will be used if client credentials
   * did not include the required "response" token.  This does not take any
   * arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_DIGEST_IN_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 223;



  /**
   * The message ID for the message that will be used if a SASL DIGEST-MD5 bind
   * request included a username that started with "dn:" but the rest of the
   * value couldn't be decoded as a DN.  This takes two arguments, which are the
   * are the provided username and a message with information about the DN
   * decoding failure.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_DECODE_USERNAME_AS_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 224;



  /**
   * The message ID for the message that will be used if a SASL DIGEST-MD5 bind
   * request included a username of "dn:" (i.e., a zero-length DN).  This does
   * not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_USERNAME_IS_NULL_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 225;



  /**
   * The message ID for the message that will be used if it is not possible to
   * obtain a read lock on an entry targeted by a SASL DIGEST-MD5 bind request.
   * This takes a single argument, which is the DN of the target entry.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 226;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve an entry by DN for a SASL DIGEST-MD5 bind request.
   * This takes two arguments, which are the DN of the entry and a message with
   * information about the reason it could not be retrieved.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_ENTRY_BY_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 227;



  /**
   * The message ID for the message that will be used if a SASL DIGEST-MD5 bind
   * request included a zero-length username.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_ZERO_LENGTH_USERNAME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 228;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to perform an internal search to resolve a username to a user
   * entry.  This takes three arguments, which are the username, a string
   * representation of the result code, and the error message from the search
   * operation.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_PERFORM_INTERNAL_SEARCH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 229;



  /**
   * The message ID for the message that will be used if an internal search
   * to resolve the provided username matches multiple entries.  This takes a
   * single argument, which is the username.
   */
  public static final int MSGID_SASLDIGESTMD5_MULTIPLE_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 230;



  /**
   * The message ID for the message that will be used if an internal search
   * to resolve the provided username does not match any entries.  This takes a
   * single argument, which is the username.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 231;



  /**
   * The message ID for the message that will be used if a user entry does not
   * contain a password attribute.  This takes a single argument, which is the
   * name or OID of the password attribute.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_PW_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 232;



  /**
   * The message ID for the message that will be used if a user entry has a
   * password with an unrecognized storage scheme.  This takes two arguments,
   * which are the DN of the user and the name of the unknown storage scheme.
   */
  public static final int MSGID_SASLDIGESTMD5_UNKNOWN_STORAGE_SCHEME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 233;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to obtain the clear-text version of a password that is encoded with
   * what should be a reversible storage scheme.  This takes three arguments,
   * which are the DN of the user, the name of the storage scheme, and a message
   * explaining the problem.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_CLEAR_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 234;



  /**
   * The message ID for the message that will be used the provided DIGEST-MD5
   * credentials are invalid for the associated user account.  This does not
   * take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_CREDENTIALS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 235;



  /**
   * The message ID for the message that will be used if SASL DIGEST-MD5
   * authentication fails because the user entry does not have any passwords
   * stored in a reversible form.  This takes a single argument, which is the
   * DN of the user.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_REVERSIBLE_PASSWORDS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 236;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to generate the DIGEST-MD5 response on the server to compare with
   * the value provided by the client.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GENERATE_RESPONSE_DIGEST =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 237;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to generate the DIGEST-MD5 response auth digest.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_SASLDIGESTMD5_CANNOT_GENERATE_RESPONSE_AUTH_DIGEST =
            CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 238;



  /**
   * The message ID for the message that will be used if the client credentials
   * include a quoted token value in which the closing quote is not followed by
   * either a comma or the end of the credentials string.  This takes a single
   * argument, which is the position of the invalid quotation mark.
   */
  public static final int MSGID_SASLDIGESTMD5_INVALID_CLOSING_QUOTE_POS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 239;



  /**
   * The message ID for the message that will be used to indicate that the
   * attribute for username lookups has been updated.  This takes two arguments,
   * which are the DN of the configuration entry and the new attribute type name
   * or OID.
   */
  public static final int MSGID_SASLDIGESTMD5_UPDATED_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 240;



  /**
   * The message ID for the message that will be used to indicate that the base
   * DN for username lookups has been updated.  This takes two arguments, which
   * are the DN of the configuration entry and the new base DN.
   */
  public static final int MSGID_SASLDIGESTMD5_UPDATED_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 241;



  /**
   * The message ID for the message that will be used to indicate that the realm
   * for DIGEST-MD5 authentication has been updated with a new value.  This
   * takes two arguments, which are the DN of the configuration entry and the
   * new realm.
   */
  public static final int MSGID_SASLDIGESTMD5_UPDATED_NEW_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 242;



  /**
   * The message ID for the message that will be used to indicate that the realm
   * for DIGEST-MD5 authentication has been updated to have no value.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_SASLDIGESTMD5_UPDATED_NO_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 243;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the username attribute used to locate entries
   * when authenticating via SASL GSSAPI.  This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_USERNAME_ATTRIBUTE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 244;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the attribute to use for username lookups.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 245;



  /**
   * The message ID for the message that will be used if the username attribute
   * is not defined in the server schema.  This takes two arguments, which are
   * the name of the username attribute and the DN of the configuration entry.
   */
  public static final int MSGID_SASLGSSAPI_UNKNOWN_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 246;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the user base DN used to locate entries when
   * authenticating via SASL GSSAPI.  This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 247;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the base DN to use for username lookups.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 248;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the server FQDN used when authenticating via SASL
   * GSSAPI.  This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_SERVER_FQDN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 249;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the server FQDN.  This takes two arguments, which are
   * the DN of the configuration entry and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 250;



  /**
   * The message ID for the message that will be used to indicate that the
   * attribute for username lookups has been updated.  This takes two arguments,
   * which are the DN of the configuration entry and the new attribute type name
   * or OID.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_USERNAME_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 251;



  /**
   * The message ID for the message that will be used to indicate that the base
   * DN for username lookups has been updated.  This takes two arguments, which
   * are the DN of the configuration entry and the new base DN.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_USER_BASE_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 252;



  /**
   * The message ID for the message that will be used to indicate that the
   * fully-qualified domain name of the server used for GSSAPI authentication
   * has been updated with a new value.  This takes two arguments, which are the
   * DN of the configuration entry and the new FQDN.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_NEW_SERVER_FQDN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 253;



  /**
   * The message ID for the message that will be used to indicate that the FQDN
   * for GSSAPI authentication has been updated to have no value.  This
   * takes a single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_NO_SERVER_FQDN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 254;



  /**
   * The message ID for the message that will be used to indicate that an
   * unexpected callback has been provided to the SASL server during GSSAPI
   * authentication.  This takes a single argument, which is a string
   * representation of the unexpected callback.
   */
  public static final int MSGID_SASLGSSAPI_UNEXPECTED_CALLBACK =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 255;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the address of the KDC to use when authenticating
   * via SASL GSSAPI.  This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_KDC_ADDRESS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 256;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the address of the KDC.  This takes two arguments,
   * which are the DN of the configuration entry and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_KDC_ADDRESS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 257;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the default realm to use when authenticating via
   * SASL GSSAPI.  This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 258;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the default realm for GSSAPI authentication.  This
   * takes two arguments, which are the DN of the configuration entry and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 259;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * process a GSSAPI bind request when no client connection is associated with
   * the bind request.  This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_NO_CLIENT_CONNECTION =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 260;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create the SASL server instance to use when processing a
   * GSSAPI bind.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_CREATE_SASL_SERVER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 261;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to evaluate the client's GSSAPI challenge response.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_EVALUATE_RESPONSE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 262;



  /**
   * The message ID for the message that will be used if the GSSAPI
   * authentication completes but there is no internal authorization ID that
   * can be used to map the authenticating user to a Directory Server user.
   * This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_NO_AUTHZ_ID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 263;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to perform an internal search to map the GSSAPI authorization ID
   * to a Directory Server user.  This takes three arguments, which are the
   * authorization ID, the result code, and the error message from the internal
   * search failure.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_PERFORM_INTERNAL_SEARCH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 264;



  /**
   * The message ID for the message that will be used if the GSSAPI
   * authorization ID appears to map to more than one user.  This takes a
   * single argument, which is the authorization ID.
   */
  public static final int MSGID_SASLGSSAPI_MULTIPLE_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 265;



  /**
   * The message ID for the message that will be used if the GSSAPI
   * authorization ID cannot be mapped to any users in the Directory Server.
   * This takes a single argument, which is the authorization ID.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_MAP_AUTHZID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 266;



  /**
   * The message ID for the message that will be used to indicate that the KDC
   * address used for GSSAPI authentication has been updated with a new value.
   * This takes two arguments, which are the DN of the configuration entry and
   * the new KDC address.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_KDC =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 267;



  /**
   * The message ID for the message that will be used to indicate that the KDC
   * address used for GSSAPI authentication has been unset as a system property
   * and therefore the underlying OS config will be used.  This takes a single
   * argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_SASLGSSAPI_UNSET_KDC =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 268;



  /**
   * The message ID for the message that will be used to indicate that the
   * default realm for GSSAPI authentication has been updated with a new value.
   * This takes two arguments, which are the DN of the configuration entry and
   * the new realm.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 269;



  /**
   * The message ID for the message that will be used to indicate that the
   * default realm used for GSSAPI authentication has been unset as a system
   * property and therefore the underlying OS config will be used.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_SASLGSSAPI_UNSET_REALM =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 270;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a JAAS login context for GSSAPI authentication.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_CREATE_LOGIN_CONTEXT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 271;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform server-side authentication needed for processing a GSSAPI
   * bind request.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_AUTHENTICATE_SERVER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 272;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the path to the Kerberos keytab file.  This does
   * not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_KEYTAB_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 273;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the path to the keytab file to use for GSSAPI
   * authentication.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_KEYTAB_FILE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 274;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to write a temporary JAAS configuration file for use when processing
   * GSSAPI authentication.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 275;



  /**
   * The message ID for the message that will be used if the GSSAPI
   * authorization ID is different from the authentication ID.  This takes
   * two arguments, which are the authentication ID and the authorization ID.
   */
  public static final int MSGID_SASLGSSAPI_DIFFERENT_AUTHID_AND_AUTHZID =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 276;



  /**
   * The message ID for the message that will be used if a "Who Am I?" request
   * is received but no client connection structure is available.  This does not
   * take any arguments.
   */
  public static final int MSGID_EXTOP_WHOAMI_NO_CLIENT_CONNECTION =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 277;



  /**
   * The message ID for the message that will be used as the description of the
   * soft reference entry cache lock timeout configuration attribute.  This does
   * not take any arguments.
   */
  public static final int MSGID_SOFTREFCACHE_DESCRIPTION_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 278;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the lock timeout to use for the soft reference entry
   * cache.  This takes three arguments, which are the DN of the configuration
   * entry, a string representation of the exception that was caught, and the
   * default lock timeout that will be used.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 279;



  /**
   * The message ID for the message that will be used as the description of the
   * soft reference entry cache include filters configuration attribute.  This
   * does not take any arguments.
   */
  public static final int MSGID_SOFTREFCACHE_DESCRIPTION_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 280;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an include filter.  This takes three arguments, which are
   * the invalid filter string, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DECODE_INCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 281;



  /**
   * The message ID for the message that will be used if none of the include
   * filter strings can be parsed.  This takes a single argument, which is the
   * DN of the configuration entry.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 282;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the include filters to use for the soft reference entry
   * cache.  This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 283;



  /**
   * The message ID for the message that will be used as the description of the
   * soft reference entry cache exclude filters configuration attribute.  This
   * does not take any arguments.
   */
  public static final int MSGID_SOFTREFCACHE_DESCRIPTION_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 284;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an exclude filter.  This takes three arguments, which are
   * the invalid filter string, the DN of the configuration entry, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DECODE_EXCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 285;



  /**
   * The message ID for the message that will be used if none of the exclude
   * filter strings can be parsed.  This takes a single argument, which is the
   * DN of the configuration entry.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_WARNING | 286;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the exclude filters to use for the soft reference entry
   * cache.  This takes two arguments, which are the DN of the configuration
   * entry and a string representation of the exception that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 287;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid lock timeout.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SOFTREFCACHE_INVALID_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 288;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid include filter.  This takes three arguments, which are the DN
   * of the configuration entry, the invalid filter string, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 289;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid set of include filters.  This takes two arguments, which are the DN
   * of the configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 290;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid exclude filter.  This takes three arguments, which are the DN
   * of the configuration entry, the invalid filter string, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 291;



  /**
   * The message ID for the message that will be used if the user requested an
   * invalid set of exclude filters.  This takes two arguments, which are the DN
   * of the configuration entry and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_SEVERE_ERROR | 292;



  /**
   * The message ID for the message that will be used if the cache lock timeout
   * has been updated.  This takes a single argument, which is the new lock
   * timeout.
   */
  public static final int MSGID_SOFTREFCACHE_UPDATED_LOCK_TIMEOUT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 293;



  /**
   * The message ID for the message that will be used if the cache include
   * filter set has been updated.  This does not take any arguments.
   */
  public static final int MSGID_SOFTREFCACHE_UPDATED_INCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 294;



  /**
   * The message ID for the message that will be used if the cache exclude
   * filter set has been updated.  This does not take any arguments.
   */
  public static final int MSGID_SOFTREFCACHE_UPDATED_EXCLUDE_FILTERS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 295;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies which attributes to use when
   * matching ID strings to users.  This does not take any arguments.
   */
  public static final int MSGID_EXACTMAP_DESCRIPTION_MATCH_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 298;



  /**
   * The message ID for the message that will be used if there were no values
   * provided for the match attribute.  This takes a single argument, which is
   * the DN of the configuration entry.
   */
  public static final int MSGID_EXACTMAP_NO_MATCH_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 299;



  /**
   * The message ID for the message that will be used if any of the match
   * attributes is not defined in the server schema.  This takes two arguments,
   * which are the DN of the configuration entry and the value of the provided
   * attribute.
   */
  public static final int MSGID_EXACTMAP_UNKNOWN_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 300;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the set of match attributes.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 301;



  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute that specifies the search bases to use when
   * matching ID strings to users.  This does not take any arguments.
   */
  public static final int MSGID_EXACTMAP_DESCRIPTION_SEARCH_BASE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 302;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the set of search bases.  This takes two arguments,
   * which are the DN of the configuration entry and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_BASE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 303;



  /**
   * The message ID for the message that will be used to indicate that the set
   * of match attributes has been updated.  This takes a single argument, which
   * is the DN of the configuration entry.
   */
  public static final int MSGID_EXACTMAP_UPDATED_MATCH_ATTRS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 304;



  /**
   * The message ID for the message that will be used to indicate that the set
   * of search bases has been updated.  This takes a single argument, which is
   * the DN of the configuration entry.
   */
  public static final int MSGID_EXACTMAP_UPDATED_MATCH_BASES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 305;



  /**
   * The message ID for the message that will be used if the search to map a
   * user returned multiple entries.  This takes a single argument, which is the
   * provided ID string.
   */
  public static final int MSGID_EXACTMAP_MULTIPLE_MATCHING_ENTRIES =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 306;



  /**
   * The message ID for the message that will be used if the search to map a
   * user could not be processed efficiently.  This two arguments, which are the
   * provided ID string and the error message from the internal search.
   */
  public static final int MSGID_EXACTMAP_INEFFICIENT_SEARCH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 307;



  /**
   * The message ID for the message that will be used if the search to map a
   * user could failed for some reason.  This two arguments, which are the
   * provided ID string and the error message from the internal search.
   */
  public static final int MSGID_EXACTMAP_SEARCH_FAILED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 308;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the DN of the configuration entry that defines
   * the identity mapper to use in conjunction with the CRAM-MD5 SASL mechanism.
   * This does not take any arguments.
   */
  public static final int MSGID_SASLCRAMMD5_DESCRIPTION_IDENTITY_MAPPER_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 309;



  /**
   * The message ID for the message that will be used if the CRAM-MD5 handler
   * configuration entry does not have an attribute that specifies which
   * identity mapper should be used.  This takes a single argument, which is the
   * DN of the SASL CRAM-MD5 configuration entry.
   */
  public static final int MSGID_SASLCRAMMD5_NO_IDENTITY_MAPPER_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 310;



  /**
   * The message ID for the message that will be used if the identity mapper DN
   * specified in the CRAM-MD5 handler entry does not refer to an active
   * identity mapper.  This takes two arguments, which are the DN of the
   * specified identity mapper and the DN of the SASL CRAM-MD5 configuration
   * entry.
   */
  public static final int MSGID_SASLCRAMMD5_NO_SUCH_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 311;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine which identity mapper to use in conjunction with the
   * CRAM-MD5 SASL mechanism.  This takes two arguments, which are the DN of
   * the configuration entry and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_GET_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 312;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to map the CRAM-MD5 username to a user entry.  This takes two
   * arguments, which are the provided username and a message that explains the
   * problem that occurred.
   */
  public static final int MSGID_SASLCRAMMD5_CANNOT_MAP_USERNAME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 313;



  /**
   * The message ID for the message that will be used if the identity mapper for
   * the CRAM-MD5 SASL mechanism is updated.  This takes two arguments, which
   * are the DN of the configuration entry and the
   */
  public static final int MSGID_SASLCRAMMD5_UPDATED_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 314;


  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the DN of the configuration entry that defines
   * the identity mapper to use in conjunction with the DIGEST-MD5 SASL
   * mechanism.  This does not take any arguments.
   */
  public static final int MSGID_SASLDIGESTMD5_DESCRIPTION_IDENTITY_MAPPER_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 315;



  /**
   * The message ID for the message that will be used if the DIGEST-MD5 handler
   * configuration entry does not have an attribute that specifies which
   * identity mapper should be used.  This takes a single argument, which is the
   * DN of the SASL DIGEST-MD5 configuration entry.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_IDENTITY_MAPPER_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 316;



  /**
   * The message ID for the message that will be used if the identity mapper DN
   * specified in the DIGEST-MD5 handler entry does not refer to an active
   * identity mapper.  This takes two arguments, which are the DN of the
   * specified identity mapper and the DN of the SASL DIGEST-MD5 configuration
   * entry.
   */
  public static final int MSGID_SASLDIGESTMD5_NO_SUCH_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 317;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine which identity mapper to use in conjunction with the
   * DIGEST-MD5 SASL mechanism.  This takes two arguments, which are the DN of
   * the configuration entry and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_GET_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 318;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to map the DIGEST-MD5 username to a user entry.  This takes two
   * arguments, which are the provided username and a message that explains the
   * problem that occurred.
   */
  public static final int MSGID_SASLDIGESTMD5_CANNOT_MAP_USERNAME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 319;



  /**
   * The message ID for the message that will be used if the identity mapper for
   * the DIGEST-MD5 SASL mechanism is updated.  This takes two arguments, which
   * are the DN of the configuration entry and the
   */
  public static final int MSGID_SASLDIGESTMD5_UPDATED_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 320;



  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the DN of the configuration entry that defines
   * the identity mapper to use in conjunction with the CRAM-MD5 SASL mechanism.
   * This does not take any arguments.
   */
  public static final int MSGID_SASLPLAIN_DESCRIPTION_IDENTITY_MAPPER_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 321;



  /**
   * The message ID for the message that will be used if the CRAM-MD5 handler
   * configuration entry does not have an attribute that specifies which
   * identity mapper should be used.  This takes a single argument, which is the
   * DN of the SASL CRAM-MD5 configuration entry.
   */
  public static final int MSGID_SASLPLAIN_NO_IDENTITY_MAPPER_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 322;



  /**
   * The message ID for the message that will be used if the identity mapper DN
   * specified in the CRAM-MD5 handler entry does not refer to an active
   * identity mapper.  This takes two arguments, which are the DN of the
   * specified identity mapper and the DN of the SASL CRAM-MD5 configuration
   * entry.
   */
  public static final int MSGID_SASLPLAIN_NO_SUCH_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 323;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine which identity mapper to use in conjunction with the
   * CRAM-MD5 SASL mechanism.  This takes two arguments, which are the DN of
   * the configuration entry and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_GET_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 324;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to map the provided username to a user entry.  This takes two
   * arguments, which are the provided username and a message that explains the
   * problem that occurred.
   */
  public static final int MSGID_SASLPLAIN_CANNOT_MAP_USERNAME =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 325;



  /**
   * The message ID for the message that will be used if the identity mapper for
   * the PLAIN SASL mechanism is updated.  This takes two arguments, which
   * are the DN of the configuration entry and the DN of the identity mapper
   * entry.
   */
  public static final int MSGID_SASLPLAIN_UPDATED_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 326;



  /**
   * The message ID for the message that will be used if a cancel request does
   * not contain a value.  This does not take any arguments.
   */
  public static final int MSGID_EXTOP_CANCEL_NO_REQUEST_VALUE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 327;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode a cancel request value.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 328;



  /**
   * The message ID for the message that will be used as the cancel reason for
   * the operation that is to be canceled.  This takes a single argument, which
   * is the message ID of the cancel request.
   */
  public static final int MSGID_EXTOP_CANCEL_REASON =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 329;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * use the authentication password syntax in conjunction with a storage scheme
   * that does not support that use.  This takes a single argument, which is the
   * name of the password storage scheme.
   */
  public static final int MSGID_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 330;



  /**
   * The message ID for the message that will be used as the description of the
   * minimum length configuration attribute.  It does not take any arguments.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MIN_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 331;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the minimum password length.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MIN_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 332;



  /**
   * The message ID for the message that will be used as the description of the
   * maximum length configuration attribute.  It does not take any arguments.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MAX_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 333;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to determine the maximum password length.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MAX_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 334;



  /**
   * The message ID for the message that will be used if the minimum password
   * length is greater than the maximum.  This takes two arguments, which are
   * the minimum and maximum allowed password lengths.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 335;



  /**
   * The message ID for the message that will be used if a provided password is
   * too short.  This takes a single argument, which is the minimum required
   * password length.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_TOO_SHORT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 336;



  /**
   * The message ID for the message that will be used if a provided password is
   * too short.  This takes a single argument, which is the maximum allowed
   * password length.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_TOO_LONG =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 337;



  /**
   * The message ID for the message that will be used to indicate that the
   * minimum password length has been updated.  This takes a single argument,
   * which is the new minimum length.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_UPDATED_MIN_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 338;



  /**
   * The message ID for the message that will be used to indicate that the
   * maximum password length has been updated.  This takes a single argument,
   * which is the new maximum length.
   */
  public static final int MSGID_PWLENGTHVALIDATOR_UPDATED_MAX_LENGTH =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 339;



  /**
   * The message ID for the message that will be used as the description for
   * the character set definitions attribute.  This does not take any arguments.
   */
  public static final int MSGID_RANDOMPWGEN_DESCRIPTION_CHARSET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 340;



  /**
   * The message ID for the message that will be used if no character set
   * definitions are included.  This takes a single argument, which is the DN of
   * the configuration entry.
   */
  public static final int MSGID_RANDOMPWGEN_NO_CHARSETS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 341;



  /**
   * The message ID for the message that will be used if two or more character
   * sets have the same name.  This takes two arguments, which are the DN of the
   * entry and the conflicting character set name.
   */
  public static final int MSGID_RANDOMPWGEN_CHARSET_NAME_CONFLICT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 342;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to decode the character set definitions.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 343;



  /**
   * The message ID for the message that will be used as the description for
   * the character set format attribute.  This does not take any arguments.
   */
  public static final int MSGID_RANDOMPWGEN_DESCRIPTION_PWFORMAT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 344;



  /**
   * The message ID for the message that will be used if no password format is
   * provided.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_RANDOMPWGEN_NO_PWFORMAT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 345;



  /**
   * The message ID for the message that will be used if the password format
   * references an unknown character set.  This takes two arguments, which are
   * the password format string and the name of the unrecognized character set.
   */
  public static final int MSGID_RANDOMPWGEN_UNKNOWN_CHARSET =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 346;



  /**
   * The message ID for the message that will be used if the password format
   * string does not have a valid syntax.  This takes a single argument, which
   * is the invalid format string.
   */
  public static final int MSGID_RANDOMPWGEN_INVALID_PWFORMAT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 347;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the password format string.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 348;


  /**
   * The message ID for the message that will be used as the description of the
   * attribute used to specify the DN of the configuration entry that defines
   * the identity mapper to use in conjunction with the GSSAPI SASL mechanism.
   * This does not take any arguments.
   */
  public static final int MSGID_SASLGSSAPI_DESCRIPTION_IDENTITY_MAPPER_DN =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 349;



  /**
   * The message ID for the message that will be used if the GSSAPI handler
   * configuration entry does not have an attribute that specifies which
   * identity mapper should be used.  This takes a single argument, which is the
   * DN of the SASL GSSAPI configuration entry.
   */
  public static final int MSGID_SASLGSSAPI_NO_IDENTITY_MAPPER_ATTR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 350;



  /**
   * The message ID for the message that will be used if the identity mapper DN
   * specified in the GSSAPI handler entry does not refer to an active identity
   * mapper.  This takes two arguments, which are the DN of the specified
   * identity mapper and the DN of the SASL GSSAPI configuration entry.
   */
  public static final int MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 351;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine which identity mapper to use in conjunction with the
   * DIGEST-MD5 SASL mechanism.  This takes two arguments, which are the DN of
   * the configuration entry and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_SASLGSSAPI_CANNOT_GET_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_MILD_ERROR | 352;



  /**
   * The message ID for the message that will be used to indicate that the
   * identity mapper used for GSSAPI authentication has been updated with a new
   * value.  This takes two arguments, which are the DN of the configuration
   * entry and the new identity mapper DN.
   */
  public static final int MSGID_SASLGSSAPI_UPDATED_IDENTITY_MAPPER =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 353;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to retrieve the password policy for the user.  This takes two
   * arguments, which are the user DN and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_EXTOP_PASSMOD_CANNOT_GET_PW_POLICY =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 354;



  /**
   * The message ID for the message that will be used if a user password change
   * is rejected because the current password was not provided.  This does not
   * take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_REQUIRE_CURRENT_PW =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 355;



  /**
   * The message ID for the message that will be used if a user password change
   * is rejected because the current password was provided over an insecure
   * communication channel.  This does not take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_SECURE_AUTH_REQUIRED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 356;



  /**
   * The message ID for the message that will be used if a user password change
   * is rejected because users are not allowed to change their passwords.  This
   * does not take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_USER_PW_CHANGES_NOT_ALLOWED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 357;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because the new password was provided over an insecure
   * communication channel.  This does not take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_SECURE_CHANGES_REQUIRED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 358;



  /**
   * The message ID for the message that will be used if a user password change
   * is rejected because the current password is too young.  This does not take
   * any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_IN_MIN_AGE =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 359;



  /**
   * The message ID for the message that will be used if a user password change
   * is rejected because the current password is expired.  This does not take
   * any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_PASSWORD_IS_EXPIRED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 360;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because no new password was given and there is no password
   * generator defined.  This does not take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_NO_PW_GENERATOR =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 361;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to use the password generator to create a new password.  This takes
   * a single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_EXTOP_PASSMOD_CANNOT_GENERATE_PW =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 362;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because the new password provided was pre-encoded.  This does not
   * take any arguments.
   */
  public static final int MSGID_EXTOP_PASSMOD_PRE_ENCODED_NOT_ALLOWED =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 363;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because the new password was rejected by a password validator.
   * This takes a single argument, which is a message explaining the rejection.
   */
  public static final int MSGID_EXTOP_PASSMOD_UNACCEPTABLE_PW =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 364;



  /**
   * The message ID for the message that will be used if a password change is
   * rejected because the new password could not be encoded using the default
   * schemes.  This takes a single argument, which is a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_EXTOP_PASSMOD_CANNOT_ENCODE_PASSWORD =
       CATEGORY_MASK_EXTENSIONS | SEVERITY_MASK_INFORMATIONAL | 365;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST,
                    "An error occurred while attempting to initialize the " +
                    "message digest generator for the %s algorithm:  %s.");
    registerMessage(MSGID_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD,
                    "An error occurred while attempting to base64-decode " +
                    "the password value %s:  %s.");
    registerMessage(MSGID_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD,
                    "Password storage scheme %s does not support use with " +
                    "the authentication password attribute syntax.");
    registerMessage(MSGID_PWSCHEME_NOT_REVERSIBLE,
                    "The %s password storage scheme is not reversible, so it " +
                    "is impossible to recover the plaintext version of an " +
                    "encoded password.");
    registerMessage(MSGID_PWSCHEME_CANNOT_ENCODE_PASSWORD,
                    "An unexpected error occurred while attempting to encode " +
                    "a password using the storage scheme defined in class " +
                    "%s:  %s.");


    registerMessage(MSGID_JMX_ALERT_HANDLER_CANNOT_REGISTER,
                    "An error occurred while trying to register the JMX " +
                    "alert handler with the MBean server:  %s.");


    registerMessage(MSGID_FIFOCACHE_DESCRIPTION_MAX_MEMORY_PCT,
                    "Specifies the maximum percentage of available memory " +
                    "in the JVM that the entry cache should be allowed to " +
                    "consume.  Its value should be an integer between 1 and " +
                    "100.  Changes to this configuration attribute will take " +
                    "effect immediately, although if the value is reduced " +
                    "to a percentage that is less than the current " +
                    "consumption in the JVM, it may take some time for " +
                    "existing cache items to be purged.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_MEMORY_PCT,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_FIFOCACHE_MAX_MEMORY_PCT +
                    " attribute in configuration entry %s:  %s.  The default " +
                    "of %d will be used.");
    registerMessage(MSGID_FIFOCACHE_DESCRIPTION_MAX_ENTRIES,
                    "Specifies the maximum number of entries that may be " +
                    "held in the entry cache, with a value of zero " +
                    "indicating that there should be no limit to the number " +
                    "of entries (although the memory percentage will still " +
                    "be observed).  Changes to this configuration attribute " +
                    "will take effect immediately, although if it is reduced " +
                    "to a value that is less than the number of entries " +
                    "currently held in the cache, it may take some time for " +
                    "existing cache items to be purged.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DETERMINE_MAX_ENTRIES,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_FIFOCACHE_MAX_ENTRIES +
                    " attribute in configuration entry %s:  %s.  No hard " +
                    "limit on the number of entries will be enforced, but " +
                    "the value of " + ATTR_FIFOCACHE_MAX_MEMORY_PCT +
                    " will still be observed.");
    registerMessage(MSGID_FIFOCACHE_DESCRIPTION_LOCK_TIMEOUT,
                    "Specifies the maximum length of time in milliseconds " +
                    "that the entry cache should block while attempting " +
                    "to acquire a lock for an entry.  Changes to this " +
                    "configuration attribute will take effect immediately.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_FIFOCACHE_LOCK_TIMEOUT +
                    " attribute in configuration entry %s:  %s.  The default " +
                    "of %d will be used.");
    registerMessage(MSGID_FIFOCACHE_DESCRIPTION_INCLUDE_FILTERS,
                    "Specifies a set of search filters that may be used to " +
                    "indicate which entries should be included in the entry " +
                    "cache.  Entries that do not match at least one of these " +
                    "filters will not be stored in the cache.  If no filters " +
                    "are provided, then any entry will be accepted.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately, but will not impact existing entries that " +
                    "are already held in the cache.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DECODE_INCLUDE_FILTER,
                    "An error occurred while attempting to decode the value " +
                    "\"%s\" from attribute " + ATTR_FIFOCACHE_INCLUDE_FILTER +
                    " of entry %s:  %s.  This filter will not be used when " +
                    "determining whether to store an entry in the cache.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
                    "An error occurred while attempting to decode any of the " +
                    "values from attribute " + ATTR_FIFOCACHE_INCLUDE_FILTER +
                    " of entry %s.  All entries will be considered eligible " +
                    "for inclusion in the cache.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_FIFOCACHE_INCLUDE_FILTER +
                    " attribute in configuration entry %s:  %s.  All entries " +
                    "will be considered eligible for inclusion in the cache.");
    registerMessage(MSGID_FIFOCACHE_DESCRIPTION_EXCLUDE_FILTERS,
                    "Specifies a set of search filters that may be used to " +
                    "indicate which entries should be excluded from the " +
                    "entry cache.  Entries that match any of these filters " +
                    "will not be stored in the cache.  If no filters are " +
                    "provided, then any entry will be accepted.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately, but will not impact existing entries that " +
                    "are already held in the cache.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
                    "An error occurred while attempting to decode the value " +
                    "\"%s\" from attribute " + ATTR_FIFOCACHE_EXCLUDE_FILTER +
                    " of entry %s:  %s.  This filter will not be used when " +
                    "determining whether to store an entry in the cache.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
                    "An error occurred while attempting to decode any of the " +
                    "values from attribute " + ATTR_FIFOCACHE_EXCLUDE_FILTER +
                    " of entry %s.  All entries will be considered eligible " +
                    "for inclusion in the cache.");
    registerMessage(MSGID_FIFOCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_FIFOCACHE_EXCLUDE_FILTER +
                    " attribute in configuration entry %s:  %s.  All entries " +
                    "will be considered eligible for inclusion in the cache.");
    registerMessage(MSGID_FIFOCACHE_INVALID_MAX_MEMORY_PCT,
                    "The " + ATTR_FIFOCACHE_MAX_MEMORY_PCT + " attribute of " +
                    "entry %s, which holds the maximum percentage of JVM " +
                    "memory available for use in the entry cache, has an " +
                    "invalid value:  %s.  Its value must be an integer " +
                    "between 1 and 100.");
    registerMessage(MSGID_FIFOCACHE_INVALID_MAX_ENTRIES,
                    "The " + ATTR_FIFOCACHE_MAX_ENTRIES + " attribute of " +
                    "entry %s, which specifies the maximum number of entries " +
                    "that may be held in the entry cache, has an invalid " +
                    "value:  %s.  Its value must be a positive integer, or " +
                    "zero to indicate that no limit should be enforced.");
    registerMessage(MSGID_FIFOCACHE_INVALID_LOCK_TIMEOUT,
                    "The " + ATTR_FIFOCACHE_LOCK_TIMEOUT + " attribute of " +
                    "entry %s, which specifies the maximum length of time in " +
                    "milliseconds that the cache should block while " +
                    "attempting to obtain a lock on an entry, has an invalid " +
                    "value:  %s.  Its value must be a positive integer, or " +
                    "zero to indicate that it should never block.");
    registerMessage(MSGID_FIFOCACHE_INVALID_INCLUDE_FILTER,
                    "The " + ATTR_FIFOCACHE_INCLUDE_FILTER + " attribute of " +
                    "entry %s, which specifies a set of search filters that " +
                    "may be used to control which entries are included in " +
                    "the cache, has an invalid value of \"%s\":  %s.");
    registerMessage(MSGID_FIFOCACHE_INVALID_INCLUDE_FILTERS,
                    "The " + ATTR_FIFOCACHE_INCLUDE_FILTER + " attribute of " +
                    "entry %s, which specifies a set of search filters that " +
                    "may be used to control which entries are included in " +
                    "the cache, has an invalid value:  %s.");
    registerMessage(MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTER,
                    "The " + ATTR_FIFOCACHE_EXCLUDE_FILTER + " attribute of " +
                    "entry %s, which specifies a set of search filters that " +
                    "may be used to control which entries are excluded from " +
                    "the cache, has an invalid value of \"%s\":  %s.");
    registerMessage(MSGID_FIFOCACHE_INVALID_EXCLUDE_FILTERS,
                    "The " + ATTR_FIFOCACHE_EXCLUDE_FILTER + " attribute of " +
                    "entry %s, which specifies a set of search filters that " +
                    "may be used to control which entries are excluded from " +
                    "the cache, has an invalid value:  %s.");
    registerMessage(MSGID_FIFOCACHE_UPDATED_MAX_MEMORY_PCT,
                    "The amount of memory that may be used for the entry " +
                    "cache has been updated to %d percent of the total " +
                    "memory available to the JVM, or approximately %d " +
                    "bytes.  If this percentage has been reduced, it may " +
                    "take some time for entries to be purged so that the " +
                    "current cache memory consumption can reflect this new " +
                    "setting.");
    registerMessage(MSGID_FIFOCACHE_UPDATED_MAX_ENTRIES,
                    "The number of entries that may be held in the entry " +
                    "cache has been updated to %d.  If this value has been " +
                    "reduced, it may take some time for entries to be purged " +
                    "so that the cache can reflect this new setting.");
    registerMessage(MSGID_FIFOCACHE_UPDATED_LOCK_TIMEOUT,
                    "The lock timeout that will be used to determine the " +
                    "length of time that the cache should block while " +
                    "attempting to acquire a lock for an entry has been " +
                    "set to %d milliseconds.");
    registerMessage(MSGID_FIFOCACHE_UPDATED_INCLUDE_FILTERS,
                    "The set of search filters that will control which " +
                    "entries may be included in the cache has been updated.");
    registerMessage(MSGID_FIFOCACHE_UPDATED_INCLUDE_FILTERS,
                    "The set of search filters that will control which " +
                    "entries should be be excluded from the cache has been " +
                    "updated.");


    registerMessage(MSGID_EXTOP_PASSMOD_ILLEGAL_REQUEST_ELEMENT_TYPE,
                    "The password modify extended request sequence included " +
                    "an ASN.1 element of an invalid type:  %s.");
    registerMessage(MSGID_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST,
                    "An unexpected error occurred while attempting to decode " +
                    "the password modify extended request sequence:  %s.");
    registerMessage(MSGID_EXTOP_PASSMOD_NO_AUTH_OR_USERID,
                    "The password modify extended request cannot be " +
                    "processed because it does not contain an authorization " +
                    "ID and the underlying connection is not authenticated.");
    registerMessage(MSGID_EXTOP_PASSMOD_CANNOT_LOCK_USER_ENTRY,
                    "The password modify extended request cannot be " +
                    "processed because the server was unable to obtain a " +
                    "write lock on user entry %s after multiple attempts.");
    registerMessage(MSGID_EXTOP_PASSMOD_CANNOT_DECODE_AUTHZ_DN,
                    "The password modify extended request cannot be " +
                    "processed because the server cannot decode \"%s\" as a " +
                    "valid DN for use in the authorization ID for the " +
                    "operation.");
    registerMessage(MSGID_EXTOP_PASSMOD_INVALID_AUTHZID_STRING,
                    "The password modify extended request cannot be " +
                    "processed because it contained an invalid authorization " +
                    "ID that did not start with either \"dn:\" or \"u:\".  " +
                    "The provided authorization ID string was \"%s\".");
    registerMessage(MSGID_EXTOP_PASSMOD_NO_USER_ENTRY_BY_AUTHZID,
                    "The password modify extended request cannot be " +
                    "processed because it was not possible to identify the " +
                    "user entry to update based on the authorization DN of " +
                    "\"%s\".");
    registerMessage(MSGID_EXTOP_PASSMOD_NO_DN_BY_AUTHZID,
                    "The password modify extended request cannot be " +
                    "processed because the provided authorization UID of " +
                    "\"%s\" did not match any entries in the directory.");
    registerMessage(MSGID_EXTOP_PASSMOD_MULTIPLE_ENTRIES_BY_AUTHZID,
                    "The password modify extended request cannot be " +
                    "processed because the provided authorization UID of " +
                    "\"%s\" matched more than one entry in the directory.");
    registerMessage(MSGID_EXTOP_PASSMOD_INVALID_OLD_PASSWORD,
                    "The password modify extended operation cannot be " +
                    "processed because the current password provided for the " +
                    "use is invalid.");
    registerMessage(MSGID_EXTOP_PASSMOD_CANNOT_GET_PW_POLICY,
                    "An error occurred while attempting to get the " +
                    "password policy for user %s:  %s.");
    registerMessage(MSGID_EXTOP_PASSMOD_REQUIRE_CURRENT_PW,
                    "The current password must be provided for self password " +
                    "changes.");
    registerMessage(MSGID_EXTOP_PASSMOD_SECURE_AUTH_REQUIRED,
                    "Password modify operations that supply the user's " +
                    "current password must be performed over a secure " +
                    "communication channel.");
    registerMessage(MSGID_EXTOP_PASSMOD_USER_PW_CHANGES_NOT_ALLOWED,
                    "End users are not allowed to change their passwords.");
    registerMessage(MSGID_EXTOP_PASSMOD_SECURE_CHANGES_REQUIRED,
                    "Password changes must be performed over a secure " +
                    "communication channel.");
    registerMessage(MSGID_EXTOP_PASSMOD_IN_MIN_AGE,
                    "The password cannot be changed because the previous " +
                    "password change was too recent.");
    registerMessage(MSGID_EXTOP_PASSMOD_PASSWORD_IS_EXPIRED,
                    "The password cannot be changed because it is expired.");
    registerMessage(MSGID_EXTOP_PASSMOD_NO_PW_GENERATOR,
                    "No new password was provided, and no password generator " +
                    "has been defined that may be used to automatically " +
                    "create a new password.");
    registerMessage(MSGID_EXTOP_PASSMOD_CANNOT_GENERATE_PW,
                    "An error occurred while attempting to create a new " +
                    "password using the password generator:  %s.");
    registerMessage(MSGID_EXTOP_PASSMOD_PRE_ENCODED_NOT_ALLOWED,
                    "The password policy does not allow users to supply " +
                    "pre-encoded passwords.");
    registerMessage(MSGID_EXTOP_PASSMOD_UNACCEPTABLE_PW,
                    "The provided new password failed the validation checks " +
                    "defined in the server:  %s.");
    registerMessage(MSGID_EXTOP_PASSMOD_CANNOT_ENCODE_PASSWORD,
                    "Unable to encode the provided password using the " +
                    "default scheme(s):  %s.");


    registerMessage(MSGID_NULL_KEYMANAGER_NO_MANAGER,
                    "The Directory Server is unable to process an operation " +
                    "which requires access to an SSL key manager because no " +
                    "valid key manager has been defined in entry " +
                    DN_KEYMANAGER_PROVIDER_CONFIG +
                    " of the server configuration.");


    registerMessage(MSGID_FILE_KEYMANAGER_DESCRIPTION_FILE,
                    "Specifies the path to the file containing the Directory " +
                    "Server keystore information.  Changes to this " +
                    "configuration attribute will take effect the next time " +
                    "that the key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_NO_FILE_ATTR,
                    "The configuration entry %s that defines a file-based " +
                    "key manager does not contain attribute " +
                    ATTR_KEYSTORE_FILE + " that should hold the path to the " +
                    "keystore file.");
    registerMessage(MSGID_FILE_KEYMANAGER_NO_SUCH_FILE,
                    "The keystore file %s specified in attribute " +
                    ATTR_KEYSTORE_FILE + " of configuration entry %s does " +
                    " not exist.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_FILE + " in configuration entry %s:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_DESCRIPTION_TYPE,
                    "Specifies the keystore type for the Directory Server " +
                    "keystore.  Valid values should always include 'JKS' and " +
                    "'PKCS12', but different implementations may allow other " +
                    "values as well.  If no value is provided, then the " +
                    "JVM-default value will be used.  Changes to this " +
                    "configuration attribute will take effect the next time " +
                    "that the key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_INVALID_TYPE,
                    "The keystore type %s specified in attribute " +
                    ATTR_KEYSTORE_TYPE + " of configuration entry %s is not " +
                    "valid:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_TYPE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_TYPE + " in configuration entry %s:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_PROPERTY,
                    "Specifies the name of the Java property that contains " +
                    "the clear-text PIN needed to access the file-based " +
                    "key manager.  Changes to this configuration attribute " +
                    "will take effect the next time that the key manager is " +
                    "accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET,
                    "Java property %s which is specified in attribute " +
                    ATTR_KEYSTORE_PIN_PROPERTY + " of configuration entry %s " +
                    "should contain the PIN needed to access the file-based " +
                    "key manager, but this property is not set.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN_PROPERTY + " in configuration entry " +
                    "%s:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_ENVAR,
                    "Specifies the name of the environment variable that " +
                    "contains the clear-text PIN needed to access the " +
                    "file-based key manager.  Changes to this configuration " +
                    "attribute will take effect the next time that the " +
                    "key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET,
                    "Environment variable %s which is specified in attribute " +
                    ATTR_KEYSTORE_PIN_ENVAR + " of configuration entry %s " +
                    "should contain the PIN needed to access the file-based " +
                    "key manager, but this property is not set.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN_ENVAR + " in configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_FILE,
                    "Specifies the path to the text file whose only contents " +
                    "should be a single line containing the clear-text PIN " +
                    "needed to access the file-based key manager.  Changes " +
                    "to this configuration attribute will take effect the " +
                    "next time that the key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_PIN_NO_SUCH_FILE,
                    "File %s specified in attribute " + ATTR_KEYSTORE_PIN_FILE +
                    " of configuration entry %s should contain the PIN " +
                    "needed to access the file-based key manager, but this " +
                    "file does not exist.");
    registerMessage(MSGID_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ,
                    "An error occurred while trying to read the keystore PIN " +
                    "from file %s specified in configuration attribute " +
                    ATTR_KEYSTORE_PIN_FILE + " of configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_FILE_KEYMANAGER_PIN_FILE_EMPTY,
                    "File %s specified in attribute " + ATTR_KEYSTORE_PIN_FILE +
                    " of configuration entry %s should contain the PIN " +
                    "needed to access the file-based key manager, but this " +
                    "file is empty.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN_FILE + " in configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_FILE_KEYMANAGER_DESCRIPTION_PIN_ATTR,
                    "Specifies the clear-text PIN needed to access the " +
                    "file-based key manager.  Changes to this configuration " +
                    "attribute will take effect the next time that the " +
                    "key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN + " in configuration entry %s:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_NO_PIN,
                    "Configuration entry %s does not specify a means of " +
                    "determining the PIN needed to access the contents of " +
                    "the file-based key manager.  The PIN may be specified " +
                    "in a Java property (named by attribute " +
                    ATTR_KEYSTORE_PIN_PROPERTY + "), an environment " +
                    "variable (named by attribute " + ATTR_KEYSTORE_PIN_ENVAR +
                    "), a text file (named by attribute " +
                    ATTR_KEYSTORE_PIN_FILE + "), or directly in the entry " +
                    "using attribute " + ATTR_KEYSTORE_PIN + ".");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_LOAD,
                    "An error occurred while trying to load the keystore " +
                    "contents from file %s:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY,
                    "An error occurred while trying to create a key manager " +
                    "factory to access the contents of keystore file %s:  %s.");
    registerMessage(MSGID_FILE_KEYMANAGER_UPDATED_FILE,
                    "The value of the " + ATTR_KEYSTORE_FILE +
                    " attribute in configuration entry %s has been updated " +
                    "to %s.  The new value will take effect the next time " +
                    "the key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_UPDATED_TYPE,
                    "The value of the " + ATTR_KEYSTORE_TYPE +
                    " attribute in configuration entry %s has been updated " +
                    "to %s.  The new value will take effect the next time " +
                    "the key manager is accessed.");
    registerMessage(MSGID_FILE_KEYMANAGER_UPDATED_PIN,
                    "The PIN to use to access the file-based key manager has " +
                    "been updated.  The new value will take effect the next " +
                    "time the key manager is accessed.");


    registerMessage(MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_PROPERTY,
                    "Specifies the name of the Java property that contains " +
                    "the clear-text PIN needed to access the PKCS#11 key " +
                    "manager.  Changes to this configuration attribute will " +
                    "take effect the next time that the key manager is " +
                    "accessed.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET,
                    "Java property %s which is specified in attribute " +
                    ATTR_KEYSTORE_PIN_PROPERTY + " of configuration entry %s " +
                    "should contain the PIN needed to access the PKCS#11 key " +
                    "manager, but this property is not set.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN_PROPERTY + " in configuration entry " +
                    "%s:  %s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ENVAR,
                    "Specifies the name of the environment variable that " +
                    "contains the clear-text PIN needed to access the " +
                    "PKCS#11 key manager.  Changes to this configuration " +
                    "attribute will take effect the next time that the key " +
                    "manager is accessed.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET,
                    "Environment variable %s which is specified in attribute " +
                    ATTR_KEYSTORE_PIN_ENVAR + " of configuration entry %s " +
                    "should contain the PIN needed to access the PKCS#11 " +
                    "key manager, but this property is not set.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN_ENVAR + " in configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_FILE,
                    "Specifies the path to the text file whose only contents " +
                    "should be a single line containing the clear-text PIN " +
                    "needed to access the PKCS#11 key manager.  Changes to " +
                    "this configuration attribute will take effect the next " +
                    "time that the key manager is accessed.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE,
                    "File %s specified in attribute " + ATTR_KEYSTORE_PIN_FILE +
                    " of configuration entry %s should contain the PIN " +
                    "needed to access the PKCS#11 key manager, but this file " +
                    "does not exist.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ,
                    "An error occurred while trying to read the keystore PIN " +
                    "from file %s specified in configuration attribute " +
                    ATTR_KEYSTORE_PIN_FILE + " of configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_PIN_FILE_EMPTY,
                    "File %s specified in attribute " + ATTR_KEYSTORE_PIN_FILE +
                    " of configuration entry %s should contain the PIN " +
                    "needed to access the PKCS#11 key manager, but this file " +
                    "is empty.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN_FILE + " in configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ATTR,
                    "Specifies the clear-text PIN needed to access the " +
                    "PKCS#11 key manager.  Changes to this configuration " +
                    "attribute will take effect the next time that the key " +
                    "manager is accessed.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_KEYSTORE_PIN + " in configuration entry %s:  %s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_NO_PIN,
                    "Configuration entry %s does not specify a means of " +
                    "determining the PIN needed to access the contents of " +
                    "the PKCS#11 key manager.  The PIN may be specified in a " +
                    "Java property (named by attribute " +
                    ATTR_KEYSTORE_PIN_PROPERTY + "), an environment " +
                    "variable (named by attribute " + ATTR_KEYSTORE_PIN_ENVAR +
                    "), a text file (named by attribute " +
                    ATTR_KEYSTORE_PIN_FILE + "), or directly in the entry " +
                    "using attribute " + ATTR_KEYSTORE_PIN + ".");
    registerMessage(MSGID_PKCS11_KEYMANAGER_CANNOT_LOAD,
                    "An error occurred while trying to access the PKCS#11 " +
                    "key manager:  %s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_CANNOT_CREATE_FACTORY,
                    "An error occurred while trying to create a key manager " +
                    "factory to access the contents of the PKCS#11 " +
                    "keystore:  %s.");
    registerMessage(MSGID_PKCS11_KEYMANAGER_UPDATED_PIN,
                    "The PIN to use to access the PKCS#11 key manager has " +
                    "been updated.  The new value will take effect the next " +
                    "time the key manager is accessed.");


    registerMessage(MSGID_FILE_TRUSTMANAGER_DESCRIPTION_FILE,
                    "Specifies the path to the file containing the Directory " +
                    "Server trust store information.  Changes to this " +
                    "configuration attribute will take effect the next time " +
                    "that the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_NO_FILE_ATTR,
                    "The configuration entry %s that defines a file-based " +
                    "trust manager does not contain attribute " +
                    ATTR_TRUSTSTORE_FILE + " that should hold the path to " +
                    "the trust store file.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE,
                    "The trust store file %s specified in attribute " +
                    ATTR_TRUSTSTORE_FILE + " of configuration entry %s does " +
                    " not exist.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_TRUSTSTORE_FILE + " in configuration entry %s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_DESCRIPTION_TYPE,
                    "Specifies the keystore type for the Directory Server " +
                    "trust store.  Valid values should always include 'JKS' " +
                    "and 'PKCS12', but different implementations may allow " +
                    "other values as well.  If no value is provided, then " +
                    "the JVM-default value will be used.  Changes to this " +
                    "configuration attribute will take effect the next time " +
                    "that the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_INVALID_TYPE,
                    "The trust store type %s specified in attribute " +
                    ATTR_TRUSTSTORE_TYPE + " of configuration entry %s is " +
                    "not valid:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_TYPE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_TRUSTSTORE_TYPE + " in configuration entry %s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_PROPERTY,
                    "Specifies the name of the Java property that contains " +
                    "the clear-text PIN needed to access the file-based " +
                    "trust manager.  Changes to this configuration attribute " +
                    "will take effect the next time that the trust manager " +
                    "is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET,
                    "Java property %s which is specified in attribute " +
                    ATTR_TRUSTSTORE_PIN_PROPERTY + " of configuration entry " +
                    "%s should contain the PIN needed to access the " +
                    "file-based trust manager, but this property is not set.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_PROPERTY,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_TRUSTSTORE_PIN_PROPERTY + " in configuration entry " +
                    "%s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ENVAR,
                    "Specifies the name of the environment variable that " +
                    "contains the clear-text PIN needed to access the " +
                    "file-based trust manager.  Changes to this " +
                    "configuration attribute will take effect the next time " +
                    "that the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET,
                    "Environment variable %s which is specified in attribute " +
                    ATTR_TRUSTSTORE_PIN_ENVAR + " of configuration entry %s " +
                    "should contain the PIN needed to access the file-based " +
                    "trust manager, but this property is not set.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_ENVAR,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_TRUSTSTORE_PIN_ENVAR + " in configuration entry " +
                    "%s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_FILE,
                    "Specifies the path to the text file whose only contents " +
                    "should be a single line containing the clear-text PIN " +
                    "needed to access the file-based trust manager.  Changes " +
                    "to this configuration attribute will take effect the " +
                    "next time that the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE,
                    "File %s specified in attribute " +
                    ATTR_TRUSTSTORE_PIN_FILE + " of configuration entry %s " +
                    "should contain the PIN needed to access the file-based " +
                    "trust manager, but this file does not exist.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ,
                    "An error occurred while trying to read the trust store " +
                    "PIN from file %s specified in configuration attribute " +
                    ATTR_TRUSTSTORE_PIN_FILE + " of configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY,
                    "File %s specified in attribute " +
                    ATTR_TRUSTSTORE_PIN_FILE + " of configuration entry %s " +
                    "should contain the PIN needed to access the file-based " +
                    "trust manager, but this file is empty.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FILE,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_TRUSTSTORE_PIN_FILE + " in configuration entry %s:  " +
                    "%s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ATTR,
                    "Specifies the clear-text PIN needed to access the " +
                    "file-based trust manager.  Changes to this " +
                    "configuration attribute will take effect the next time " +
                    "that the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR,
                    "An unexpected error occurred while trying to determine " +
                    "the value of configuration attribute " +
                    ATTR_TRUSTSTORE_PIN + " in configuration entry %s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_LOAD,
                    "An error occurred while trying to load the trust store " +
                    "contents from file %s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY,
                    "An error occurred while trying to create a trust " +
                    "manager factory to access the contents of trust store " +
                    "file %s:  %s.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_UPDATED_FILE,
                    "The value of the " + ATTR_TRUSTSTORE_FILE +
                    " attribute in configuration entry %s has been updated " +
                    "to %s.  The new value will take effect the next time " +
                    "the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_UPDATED_TYPE,
                    "The value of the " + ATTR_TRUSTSTORE_TYPE +
                    " attribute in configuration entry %s has been updated " +
                    "to %s.  The new value will take effect the next time " +
                    "the trust manager is accessed.");
    registerMessage(MSGID_FILE_TRUSTMANAGER_UPDATED_PIN,
                    "The PIN to use to access the file-based trust manager " +
                    "has been updated.  The new value will take effect the " +
                    "next time the trust manager is accessed.");


    registerMessage(MSGID_NULL_SECURITY_PROVIDER_READ_ERROR,
                    "An unexpected error occurred while attempting to read " +
                    "data from the client using the null connection security " +
                    "provider:  %s.");
    registerMessage(MSGID_NULL_SECURITY_PROVIDER_WRITE_ERROR,
                    "An unexpected error occurred while attempting to write " +
                    "data to the client using the null connection security " +
                    "provider:  %s.");


    registerMessage(MSGID_TLS_SECURITY_PROVIDER_CANNOT_INITIALIZE,
                    "An error occurred while attempting to initialize the " +
                    "SSL context for use in the TLS connection security " +
                    "provider:  %s.");
    registerMessage(MSGID_TLS_SECURITY_PROVIDER_UNEXPECTED_UNWRAP_STATUS,
                    "An unexpected status result was returned to the TLS " +
                    "connection security provider when attempting to unwrap " +
                    "encrypted data read from the client:  %s.");
    registerMessage(MSGID_TLS_SECURITY_PROVIDER_READ_ERROR,
                    "An unexpected error occurred while attempting to read " +
                    "data from the client using the TLS connection security " +
                    "provider:  %s.");
    registerMessage(MSGID_TLS_SECURITY_PROVIDER_WRITE_NEEDS_UNWRAP,
                    "An attempt was made to write data to a client through " +
                    "the TLS connection security provider, but the SSL " +
                    "indicated that it was necessary to read data from the " +
                    "client in order to perform the SSL negotiation, but no " +
                    "data was available for reading.  This is an unexpected " +
                    "condition, and it is not possible to continue " +
                    "processing on this client connection without the " +
                    "potential for blocking other client connections, so " +
                    "connection will be closed.");
    registerMessage(MSGID_TLS_SECURITY_PROVIDER_UNEXPECTED_WRAP_STATUS,
                    "An unexpected status result was returned to the TLS " +
                    "connection security provider when attempting to wrap " +
                    "clear-text data for writing to the client:  %s.");
    registerMessage(MSGID_TLS_SECURITY_PROVIDER_WRITE_ERROR,
                    "An unexpected error occurred while attempting to write " +
                    "data to the client using the TLS connection security " +
                    "provider:  %s.");


    registerMessage(MSGID_SEDCM_NO_PEER_CERTIFICATE,
                    "Could not map the provided certificate chain to a user " +
                    "entry because no peer certificate was available.");
    registerMessage(MSGID_SEDCM_PEER_CERT_NOT_X509,
                    "Could not map the provided certificate chain to a user " +
                    "because the peer certificate was not an X.509 " +
                    "certificate (peer certificate format was %s).");
    registerMessage(MSGID_SEDCM_CANNOT_DECODE_SUBJECT_AS_DN,
                    "Could not map the provided certificate chain to a user " +
                    "because the peer certificate subject \"%s\" could not " +
                    "be decoded as an LDAP DN:  %s.");
    registerMessage(MSGID_SEDCM_CANNOT_GET_ENTRY,
                    "Could not map the provided certificate chain to a user " +
                    "because an error occurred while attempting to retrieve " +
                    "the user entry with DN \"%s\":  %s.");
    registerMessage(MSGID_SEDCM_NO_USER_FOR_DN,
                    "Could not map the provided certificate chain to a user " +
                    "because no user entry exists with a DN of %s.");
    registerMessage(MSGID_SEDCM_CANNOT_LOCK_ENTRY,
                    "The Directory Server was unable to obtain a read lock " +
                    "on user entry %s in order to retrieve that entry.");



    registerMessage(MSGID_SASLEXTERNAL_NO_CLIENT_CONNECTION,
                    "The SASL EXTERNAL bind request could not be processed " +
                    "because the associated bind request does not have a " +
                    "reference to the client connection.");
    registerMessage(MSGID_SASLEXTERNAL_NO_SECURITY_PROVIDER,
                    "The SASL EXTERNAL bind request could not be processed " +
                    "because the associated client connection does not " +
                    "have a security provider.");
    registerMessage(MSGID_SASLEXTERNAL_CLIENT_NOT_USING_TLS_PROVIDER,
                    "The SASL EXTERNAL bind request could not be processed " +
                    "because the client connection is not using the TLS " +
                    "security provider (client security provider is %s).  " +
                    "The TLS security provider is required for clients that " +
                    "wish to use SASL EXTERNAL authentication.");
    registerMessage(MSGID_SASLEXTERNAL_NO_CLIENT_CERT,
                    "The SASL EXTERNAL bind request could not be processed " +
                    "because the client did not present an certificate chain " +
                    "during SSL/TLS negotiation.");
    registerMessage(MSGID_SASLEXTERNAL_NO_MAPPING,
                    "The SASL EXTERNAL bind request failed because the " +
                    "certificate chain presented by the client during " +
                    "SSL/TLS negotiation could not be mapped to a user " +
                    "entry in the Directory Server.");
    registerMessage(MSGID_SASLEXTERNAL_DESCRIPTION_VALIDATION_POLICY,
                    "Indicates whether the SASL EXTERNAL mechanism handler " +
                    "should attempt to validate the peer certificate against " +
                    "a certificate in the corresponding user's entry.  The " +
                    "value must be one of \"true\" (which will always " +
                    "attempt to validate the certificate and will fail if " +
                    "no certificates are present), \"false\" (which will " +
                    "never attempt to validate the peer certificate), and " +
                    "\"ifpresent\" (which will validate the peer certificate " +
                    "if there are one or more certificates in the user's " +
                    "entry, but will not fail if there are no certificates " +
                    "in the entry.  Changes to this configuration attribute " +
                    "will take effect immediately.");
    registerMessage(MSGID_SASLEXTERNAL_INVALID_VALIDATION_VALUE,
                    "Configuration entry %s has an invalid value %s for " +
                    "attribute " + ATTR_CLIENT_CERT_VALIDATION_POLICY +
                    ".  The value must be one of \"always\", \"never\", or " +
                    "\"ifpresent\".");
    registerMessage(MSGID_SASLEXTERNAL_CANNOT_GET_VALIDATION_POLICY,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " +
                    ATTR_CLIENT_CERT_VALIDATION_POLICY +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLEXTERNAL_DESCRIPTION_CERTIFICATE_ATTRIBUTE,
                    "Specifies the name of the attribute that will be used " +
                    "to hold the certificate information in user entries " +
                    "for the purpose of validation.  This must specify the " +
                    "name of a valid attribute type defined in the server " +
                    "schema.  Changes to this configuration attribute will " +
                    "take effect immediately.");
    registerMessage(MSGID_SASLEXTERNAL_CANNOT_GET_CERT_ATTR,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " +
                    ATTR_VALIDATION_CERT_ATTRIBUTE +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR,
                    "The attribute %s referenced in configuration attribute " +
                    ATTR_VALIDATION_CERT_ATTRIBUTE +
                    " in configuration entry %s does not exist in the " +
                    "Directory Server schema.  The attribute that is to be " +
                    "used for certificate validation during SASL EXTERNAL " +
                    "authentication must be defined in the server schema.");
    registerMessage(MSGID_SASLEXTERNAL_NO_CERT_IN_ENTRY,
                    "Unable to authenticate via SASL EXTERNAL because the " +
                    "mapped user entry %s does not have any certificates " +
                    "with which to verify the presented peer certificate.");
    registerMessage(MSGID_SASLEXTERNAL_PEER_CERT_NOT_FOUND,
                    "Unable to authenticate via SASL EXTERNAL because the " +
                    "mapped user entry %s did not contain the peer " +
                    "certificate presented by the client.");
    registerMessage(MSGID_SASLEXTERNAL_CANNOT_VALIDATE_CERT,
                    "An error occurred while attempting to validate the peer " +
                    "certificate presented by the client with a certificate " +
                    "from the user's entry %s:  %s.");
    registerMessage(MSGID_SASLEXTERNAL_UPDATED_VALIDATION_POLICY,
                    "Attribute " + ATTR_CLIENT_CERT_VALIDATION_POLICY +
                    " in configuration entry %s has been updated.  The new " +
                    "client certificate validation policy is %s.");
    registerMessage(MSGID_SASLEXTERNAL_UPDATED_CERT_ATTR,
                    "Attribute " + ATTR_VALIDATION_CERT_ATTRIBUTE +
                    " in configuration entry %s has been updated.  The %s " +
                    "attribute will now be used when validating peer " +
                    "certificates.");


    registerMessage(MSGID_STARTTLS_NO_CLIENT_CONNECTION,
                    "StartTLS cannot be used on this connection because the " +
                    "underlying client connection is not available.");
    registerMessage(MSGID_STARTTLS_NOT_TLS_CAPABLE,
                    "StartTLS cannot be used on this client connection " +
                    "because this connection type is not capable of using " +
                    "StartTLS to protect its communication.");
    registerMessage(MSGID_STARTTLS_ERROR_ON_ENABLE,
                    "An unexpected error occurred while attempting to enable " +
                    "the TLS connection security manager on the client " +
                    "connection for the purpose of StartTLS:  %s.");



    registerMessage(MSGID_SASLPLAIN_DESCRIPTION_IDENTITY_MAPPER_DN,
                    "Specifies the DN of the configuration entry that holds " +
                    "the configuration for the identity mapper that should " +
                    "be used to map the provided username to a Directory " +
                    "Server user entry.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLPLAIN_NO_IDENTITY_MAPPER_ATTR,
                    "Configuration entry %s does not contain attribute " +
                    ATTR_IDMAPPER_DN + " which specifies the DN of the " +
                    "identity mapper to use in conjunction with the PLAIN " +
                    "SASL mechanism.  This is a required attribute.");
    registerMessage(MSGID_SASLPLAIN_NO_SUCH_IDENTITY_MAPPER,
                    "The identity mapper %s specified in attribute " +
                    ATTR_IDMAPPER_DN + " of configuration entry %s does not " +
                    "reference a valid identity mapper configuration that is " +
                    "enabled for use in the Directory Server.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_GET_IDENTITY_MAPPER,
                    "An error occurred while trying to process the value " +
                    "of the " + ATTR_IDMAPPER_DN + " attribute in " +
                    "configuration entry %s to determine which identity " +
                    "mapper should be used in conjunction with the PLAIN " +
                    "SASL mechanism:  %s.");
    registerMessage(MSGID_SASLPLAIN_DESCRIPTION_USERNAME_ATTRIBUTE,
                    "Specifies the name of the attribute that will be used " +
                    "to identify user entries based on the authcID/authzID " +
                    "provided during SASL PLAIN authentication.  This must " +
                    "specify the name of a valid attribute type defined in " +
                    "the server schema.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_GET_USERNAME_ATTR,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USERNAME_ATTRIBUTE +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLPLAIN_UNKNOWN_USERNAME_ATTR,
                    "The attribute %s referenced in configuration attribute " +
                    ATTR_USERNAME_ATTRIBUTE + " in configuration entry %s " +
                    "does not exist in the Directory Server schema.  The " +
                    "attribute that is to be used for username lookups " +
                    "during SASL PLAIN authentication must be defined in the " +
                    "server schema.");
    registerMessage(MSGID_SASLPLAIN_DESCRIPTION_USER_BASE_DN,
                    "Specifies the base DN that should be used when " +
                    "searching for entries based on the authcID/authzID " +
                    "provided during SASL PLAIN authentication.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_GET_USER_BASE_DN,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USER_BASE_DN +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLPLAIN_NO_SASL_CREDENTIALS,
                    "SASL PLAIN authentication requires that SASL " +
                    "credentials be provided but none were included in the " +
                    "bind request.");
    registerMessage(MSGID_SASLPLAIN_NO_NULLS_IN_CREDENTIALS,
                    "The SASL PLAIN bind request did not include any NULL " +
                    "characters.  NULL characters are required as delimiters " +
                    "between the authorization ID and authentication ID, and " +
                    "also between the authentication ID and the password.");
    registerMessage(MSGID_SASLPLAIN_NO_SECOND_NULL,
                    "The SASL PLAIN bind request did not include a second " +
                    "NULL character in the credentials, which is required as " +
                    "a delimiter between the authentication ID and the " +
                    "password.");
    registerMessage(MSGID_SASLPLAIN_ZERO_LENGTH_AUTHCID,
                    "The authentication ID contained in the SASL PLAIN bind " +
                    "request had a length of zero characters, which is not " +
                    "allowed.  SASL PLAIN authentication does not allow an " +
                    "empty string for use as the authentication ID.");
    registerMessage(MSGID_SASLPLAIN_ZERO_LENGTH_PASSWORD,
                    "The password contained in the SASL PLAIN bind request " +
                    "had a length of zero characters, which is not allowed.  " +
                    "SASL PLAIN authentication does not allow an empty " +
                    "string for use as the password.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_DECODE_AUTHCID_AS_DN,
                    "An error occurred while attempting to decode the SASL " +
                    "PLAIN authentication ID \"%s\" because it appeared to " +
                    "contain a DN but DN decoding failed:  %s.");
    registerMessage(MSGID_SASLPLAIN_AUTHCID_IS_NULL_DN,
                    "The authentication ID in the SASL PLAIN bind request " +
                    "appears to be an empty DN.  This is not allowed.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_GET_ENTRY_BY_DN,
                    "An error occurred while attempting to retrieve user " +
                    "entry %s as specified in the DN-based authentication ID " +
                    "of a SASL PLAIN bind request:  %s.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_MAP_USERNAME,
                    "An error occurred while attempting to map username %s " +
                    "to a Directory Server entry:  %s.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_PERFORM_INTERNAL_SEARCH,
                    "An error occurred while trying to perform an internal " +
                    "search to retrieve the user entry associated with the " +
                    "SASL PLAIN authentication ID %s.  The result of that " +
                    "search was %s with a message of %s.");
    registerMessage(MSGID_SASLPLAIN_MULTIPLE_MATCHING_ENTRIES,
                    "The internal search attempting to resolve SASL PLAIN " +
                    "authentication ID %s matched multiple entries.  " +
                    "Authentication cannot succeed unless the authentication " +
                    "ID is mapped to exactly one user entry.");
    registerMessage(MSGID_SASLPLAIN_NO_MATCHING_ENTRIES,
                    "The server was not able to find any user entries for " +
                    "the provided authentication ID of %s.");
    registerMessage(MSGID_SASLPLAIN_NO_PW_ATTR,
                    "The SASL PLAIN authentication failed because the mapped " +
                    "user entry did not contain any values for the %s " +
                    "attribute.");
    registerMessage(MSGID_SASLPLAIN_UNKNOWN_STORAGE_SCHEME,
                    "A password in the target user entry %s could not be " +
                    "processed via SASL PLAIN because that password has an " +
                    "unknown storage scheme of %s.");
    registerMessage(MSGID_SASLPLAIN_INVALID_PASSWORD,
                    "The provided password is invalid.");
    registerMessage(MSGID_SASLPLAIN_UPDATED_IDENTITY_MAPPER,
                    "Attribute " + ATTR_IDMAPPER_DN +
                    " in configuration entry %s has been updated.  The " +
                    "identity mapper defined in configuration entry %s " +
                    "will now be used to map usernames to entries when " +
                    "processing SASL PLAIN bind requests.");
    registerMessage(MSGID_SASLPLAIN_UPDATED_USERNAME_ATTR,
                    "Attribute " + ATTR_USERNAME_ATTRIBUTE +
                    " in configuration entry %s has been updated.  The %s " +
                    "attribute will now be used when looking up user entries " +
                    "based on their authcID/authzID.");
    registerMessage(MSGID_SASLPLAIN_UPDATED_USER_BASE_DN,
                    "Attribute " + ATTR_USER_BASE_DN +
                    " in configuration entry %s has been updated.  The DN %s " +
                    "will now be used as the search base when looking up " +
                    "user entries based on their authcID/authzID.");
    registerMessage(MSGID_SASLPLAIN_CANNOT_LOCK_ENTRY,
                    "The Directory Server was unable to obtain a read lock " +
                    "on user entry %s in order to retrieve that entry.");


    registerMessage(MSGID_SASLANONYMOUS_TRACE,
                    "SASL ANONYMOUS bind operation (conn=%d, op=%d) provided " +
                    "trace information:  %s.");


    registerMessage(MSGID_SASLCRAMMD5_CANNOT_GET_MESSAGE_DIGEST,
                    "An unexpected error occurred while attempting to obtain " +
                    "an MD5 digest engine for use by the CRAM-MD5 SASL " +
                    "handler:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_DESCRIPTION_IDENTITY_MAPPER_DN,
                    "Specifies the DN of the configuration entry that holds " +
                    "the configuration for the identity mapper that should " +
                    "be used to map the CRAM-MD5 username to a Directory " +
                    "Server user entry.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLCRAMMD5_NO_IDENTITY_MAPPER_ATTR,
                    "Configuration entry %s does not contain attribute " +
                    ATTR_IDMAPPER_DN + " which specifies the DN of the " +
                    "identity mapper to use in conjunction with the CRAM-MD5 " +
                    "SASL mechanism.  This is a required attribute.");
    registerMessage(MSGID_SASLCRAMMD5_NO_SUCH_IDENTITY_MAPPER,
                    "The identity mapper %s specified in attribute " +
                    ATTR_IDMAPPER_DN + " of configuration entry %s does not " +
                    "reference a valid identity mapper configuration that is " +
                    "enabled for use in the Directory Server.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_GET_IDENTITY_MAPPER,
                    "An error occurred while trying to process the value " +
                    "of the " + ATTR_IDMAPPER_DN + " attribute in " +
                    "configuration entry %s to determine which identity " +
                    "mapper should be used in conjunction with the CRAM-MD5 " +
                    "SASL mechanism:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_DESCRIPTION_USERNAME_ATTRIBUTE,
                    "Specifies the name of the attribute that will be used " +
                    "to identify user entries based on the username provided " +
                    "during SASL CRAM-MD5 authentication.  This must specify " +
                    "the name of a valid attribute type defined in the " +
                    "server schema.  Changes to this configuration attribute " +
                    "will take effect immediately.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_GET_USERNAME_ATTR,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USERNAME_ATTRIBUTE +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_UNKNOWN_USERNAME_ATTR,
                    "The attribute %s referenced in configuration attribute " +
                    ATTR_USERNAME_ATTRIBUTE + " in configuration entry %s " +
                    "does not exist in the Directory Server schema.  The " +
                    "attribute that is to be used for username lookups " +
                    "during SASL CRAM-MD5 authentication must be defined in " +
                    "the server schema.");
    registerMessage(MSGID_SASLCRAMMD5_DESCRIPTION_USER_BASE_DN,
                    "Specifies the base DN that should be used when " +
                    "searching for entries based on the username provided " +
                    "during SASL CRAM-MD5 authentication.  Changes to this " +
                    "configuration attribute will take effect immediately.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_GET_USER_BASE_DN,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USER_BASE_DN +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_NO_STORED_CHALLENGE,
                    "The SASL CRAM-MD5 bind request contained SASL " +
                    "credentials but there is no stored challenge for this " +
                    "client connection.  The first CRAM-MD5 bind request in " +
                    "the two-stage process must not contain client SASL " +
                    "credentials.");
    registerMessage(MSGID_SASLCRAMMD5_INVALID_STORED_CHALLENGE,
                    "The SASL CRAM-MD5 bind request contained SASL " +
                    "credentials, but the stored SASL state information for " +
                    "this client connection is not in an appropriate form " +
                    "for the challenge.");
    registerMessage(MSGID_SASLCRAMMD5_NO_SPACE_IN_CREDENTIALS,
                    "The SASL CRAM-MD5 bind request from the client included " +
                    "SASL credentials but there was no space to separate " +
                    "the username from the authentication digest.");
    registerMessage(MSGID_SASLCRAMMD5_INVALID_DIGEST_LENGTH,
                    "The SASL CRAM-MD5 bind request included SASL " +
                    "credentials, but the decoded digest string had an " +
                    "invalid length of %d bytes rather than the %d bytes " +
                    "expected for a hex representation of an MD5 digest.");
    registerMessage(MSGID_SASLCRAMMD5_INVALID_DIGEST_CONTENT,
                    "The SASL CRAM-MD5 bind request included SASL " +
                    "credentials, but the decoded digest was not comprised " +
                    "of only hexadecimal digits:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_DECODE_USERNAME_AS_DN,
                    "An error occurred while attempting to decode the SASL " +
                    "CRAM-MD5 username \"%s\" because it appeared to contain " +
                    "a DN but DN decoding failed:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_USERNAME_IS_NULL_DN,
                    "The username in the SASL CRAM-MD5 bind request appears " +
                    "to be an empty DN.  This is not allowed.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_LOCK_ENTRY,
                    "The Directory Server was unable to obtain a read lock " +
                    "on user entry %s in order to retrieve that entry.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_GET_ENTRY_BY_DN,
                    "An error occurred while attempting to retrieve user " +
                    "entry %s as specified in the DN-based username of a " +
                    "SASL CRAM-MD5 bind request:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_MAP_USERNAME,
                    "An error occurred while attempting to map username %s " +
                    "to a Directory Server entry:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_ZERO_LENGTH_USERNAME,
                    "The username contained in the SASL CRAM-MD5 bind " +
                    "request had a length of zero characters, which is not " +
                    "allowed.  CRAM-MD5 authentication does not allow an " +
                    "empty string for use as the username.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_PERFORM_INTERNAL_SEARCH,
                    "An error occurred while trying to perform an internal " +
                    "search to retrieve the user entry associated with the " +
                    "SASL CRAM-MD5 username %s.  The result of that " +
                    "search was %s with a message of %s.");
    registerMessage(MSGID_SASLCRAMMD5_MULTIPLE_MATCHING_ENTRIES,
                    "The internal search attempting to resolve SASL CRAM-MD5 " +
                    "username %s matched multiple entries.  Authentication " +
                    "cannot succeed unless the username is mapped to exactly " +
                    "one user entry.");
    registerMessage(MSGID_SASLCRAMMD5_NO_MATCHING_ENTRIES,
                    "The server was not able to find any user entries for " +
                    "the provided username of %s.");
    registerMessage(MSGID_SASLCRAMMD5_NO_PW_ATTR,
                    "The SASL CRAM-MD5 authentication failed because the " +
                    "mapped user entry did not contain any values for the %s " +
                    "attribute.");
    registerMessage(MSGID_SASLCRAMMD5_UNKNOWN_STORAGE_SCHEME,
                    "A password in the target user entry %s could not be " +
                    "processed via SASL CRAM-MD5 because that password has " +
                    "an unknown storage scheme of %s.");
    registerMessage(MSGID_SASLCRAMMD5_CANNOT_GET_CLEAR_PASSWORD,
                    "An error occurred while attempting to obtain the " +
                    "clear-text password for user %s from the value with " +
                    "storage scheme %s:  %s.");
    registerMessage(MSGID_SASLCRAMMD5_INVALID_PASSWORD,
                    "The provided password is invalid.");
    registerMessage(MSGID_SASLCRAMMD5_NO_REVERSIBLE_PASSWORDS,
                    "SASL CRAM-MD5 authentication is not possible for user " +
                    "%s because none of the passwords in the user entry are " +
                    "stored in a reversible form.");
    registerMessage(MSGID_SASLCRAMMD5_UPDATED_IDENTITY_MAPPER,
                    "Attribute " + ATTR_IDMAPPER_DN +
                    " in configuration entry %s has been updated.  The " +
                    "identity mapper defined in configuration entry %s " +
                    "will now be used to map usernames to entries when " +
                    "processing SASL CRAM-MD5 bind requests.");
    registerMessage(MSGID_SASLCRAMMD5_UPDATED_USERNAME_ATTR,
                    "Attribute " + ATTR_USERNAME_ATTRIBUTE +
                    " in configuration entry %s has been updated.  The %s " +
                    "attribute will now be used when looking up user entries " +
                    "based on their username.");
    registerMessage(MSGID_SASLCRAMMD5_UPDATED_USER_BASE_DN,
                    "Attribute " + ATTR_USER_BASE_DN +
                    " in configuration entry %s has been updated.  The DN %s " +
                    "will now be used as the search base when looking up " +
                    "user entries based on their username.");


    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_MESSAGE_DIGEST,
                    "An unexpected error occurred while attempting to obtain " +
                    "an MD5 digest engine for use by the DIGEST-MD5 SASL " +
                    "handler:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_DESCRIPTION_IDENTITY_MAPPER_DN,
                    "Specifies the DN of the configuration entry that holds " +
                    "the configuration for the identity mapper that should " +
                    "be used to map the DIGEST-MD5 username to a Directory " +
                    "Server user entry.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_IDENTITY_MAPPER_ATTR,
                    "Configuration entry %s does not contain attribute " +
                    ATTR_IDMAPPER_DN + " which specifies the DN of the " +
                    "identity mapper to use in conjunction with the " +
                    "DIGEST-MD5 SASL mechanism.  This is a required " +
                    "attribute.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_SUCH_IDENTITY_MAPPER,
                    "The identity mapper %s specified in attribute " +
                    ATTR_IDMAPPER_DN + " of configuration entry %s does not " +
                    "reference a valid identity mapper configuration that is " +
                    "enabled for use in the Directory Server.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_IDENTITY_MAPPER,
                    "An error occurred while trying to process the value " +
                    "of the " + ATTR_IDMAPPER_DN + " attribute in " +
                    "configuration entry %s to determine which identity " +
                    "mapper should be used in conjunction with the " +
                    "DIGEST-MD5 SASL mechanism:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_DESCRIPTION_USERNAME_ATTRIBUTE,
                    "Specifies the name of the attribute that will be used " +
                    "to identify user entries based on the username provided " +
                    "during SASL DIGEST-MD5 authentication.  This must " +
                    "specify the name of a valid attribute type defined in " +
                    "the server schema.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_USERNAME_ATTR,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USERNAME_ATTRIBUTE +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_UNKNOWN_USERNAME_ATTR,
                    "The attribute %s referenced in configuration attribute " +
                    ATTR_USERNAME_ATTRIBUTE + " in configuration entry %s " +
                    "does not exist in the Directory Server schema.  The " +
                    "attribute that is to be used for username lookups " +
                    "during SASL DIGEST-MD5 authentication must be defined " +
                    "in the server schema.");
    registerMessage(MSGID_SASLDIGESTMD5_DESCRIPTION_USER_BASE_DN,
                    "Specifies the base DN that should be used when " +
                    "searching for entries based on the username provided " +
                    "during SASL DIGEST-MD5 authentication.  Changes to this " +
                    "configuration attribute will take effect immediately.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_USER_BASE_DN,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USER_BASE_DN +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_DESCRIPTION_REALM,
                    "Specifies the realm that should be used by the server " +
                    "for DIGEST-MD5 authentication.  If this is not " +
                    "provided, then the server will default to using a set " +
                    "of realm names that correspond to the defined " +
                    "suffixes.  Changes to this configuration attribute will " +
                    "take effect immediately.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_REALM,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_DIGESTMD5_REALM +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_CHALLENGE_TOO_LONG,
                    "The initial DIGEST-MD5 must be less than 2048 bytes, " +
                    "but the generated challenge was %d bytes.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_STORED_STATE,
                    "The SASL DIGEST-MD5 bind request contained SASL " +
                    "credentials but there is no stored SASL state " +
                    "information for this client connection.  If this is " +
                    "an initial authentication, then the client must not " +
                    "provide any SASL credentials.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_STORED_STATE,
                    "The SASL DIGEST-MD5 bind request contained SASL " +
                    "credentials, but the stored SASL state information for " +
                    "this client connection is not in an appropriate form " +
                    "for the challenge.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_PARSE_ISO_CREDENTIALS,
                    "An error occurred while attempting to parse the " +
                    "DIGEST-MD5 credentials as a string using the %s " +
                    "character set:  %s.  The server will re-try using UTF-8.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_PARSE_UTF8_CREDENTIALS,
                    "An error occurred while attempting to parse the " +
                    "DIGEST-MD5 credentials as a string using the UTF-8 " +
                    "character set:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_TOKEN_IN_CREDENTIALS,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "contained an invalid token of \"%s\" starting at " +
                    "position %d.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_CHARSET,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "specified an invalid character set of %s.  Only a value " +
                    "of 'utf-8' is acceptable for this parameter.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_DECODE_REALM_AS_DN,
                    "An error occurred while attempting to parse the " +
                    "provided response realm \"%s\" as a DN:  %s");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_REALM,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "included an invalid realm of \"%s\".");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_NONCE,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "included a nonce that was different from the nonce " +
                    "supplied by the server.  This could indicate a replay " +
                    "attack or a chosen plaintext attack, and as a result " +
                    "the client connection will be terminated.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_DECODE_NONCE_COUNT,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "included a nonce count \"%s\" that could not be decoded " +
                    "as a hex-encoded integer.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_DECODE_STORED_NONCE_COUNT,
                    "An unexpected error occurred while attempting to decode " +
                    "the nonce count stored by the server for this client " +
                    "connection:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_NONCE_COUNT,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "included a nonce count that was different from the " +
                    "count expected by the server.  This could indicate a " +
                    "replay attack, and as a result the client connection " +
                    "will be terminated.");
    registerMessage(MSGID_SASLDIGESTMD5_INTEGRITY_NOT_SUPPORTED,
                    "The client requested the auth-int quality of protection " +
                    "but integrity protection is not currently supported by " +
                    "the Directory Server.");
    registerMessage(MSGID_SASLDIGESTMD5_CONFIDENTIALITY_NOT_SUPPORTED,
                    "The client requested the auth-conf quality of " +
                    "protection but confidentiality protection is not " +
                    "currently supported by the Directory Server.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_QOP,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "requested an invalid quality of protection mechanism of " +
                    "%s.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_PARSE_RESPONSE_DIGEST,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "included a digest that could not be decoded as a " +
                    "hex-encoded byte sequence:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_RESPONSE_TOKEN,
                    "The DIGEST-MD5 credentials provided by the client " +
                    "included an invalid token named \"%s\".");
    registerMessage(MSGID_SASLDIGESTMD5_NO_USERNAME_IN_RESPONSE,
                    "The DIGEST-MD5 credentials provided by the client did " +
                    "not contain the required \"username\" token.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_NONCE_IN_RESPONSE,
                    "The DIGEST-MD5 credentials provided by the client did " +
                    "not contain the required \"nonce\" token.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_CNONCE_IN_RESPONSE,
                    "The DIGEST-MD5 credentials provided by the client did " +
                    "not contain the required \"cnonce\" token.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_NONCE_COUNT_IN_RESPONSE,
                    "The DIGEST-MD5 credentials provided by the client did " +
                    "not contain the required \"nc\" token.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_DIGEST_URI_IN_RESPONSE,
                    "The DIGEST-MD5 credentials provided by the client did " +
                    "not contain the required \"digest-uri\" token.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_DIGEST_IN_RESPONSE,
                    "The DIGEST-MD5 credentials provided by the client did " +
                    "not contain the required \"response\" token.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_DECODE_USERNAME_AS_DN,
                    "An error occurred while attempting to decode the SASL " +
                    "DIGEST-MD5 username \"%s\" because it appeared to " +
                    "contain a DN but DN decoding failed:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_USERNAME_IS_NULL_DN,
                    "The username in the SASL DIGEST-MD5 bind request " +
                    "appears to be an empty DN.  This is not allowed.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_LOCK_ENTRY,
                    "The Directory Server was unable to obtain a read lock " +
                    "on user entry %s in order to retrieve that entry.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_ENTRY_BY_DN,
                    "An error occurred while attempting to retrieve user " +
                    "entry %s as specified in the DN-based username of a " +
                    "SASL DIGEST-MD5 bind request:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_ZERO_LENGTH_USERNAME,
                    "The username contained in the SASL DIGEST-MD5 bind " +
                    "request had a length of zero characters, which is not " +
                    "allowed.  DIGEST-MD5 authentication does not allow an " +
                    "empty string for use as the username.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_MAP_USERNAME,
                    "An error occurred while attempting to map username %s " +
                    "to a Directory Server entry:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_PERFORM_INTERNAL_SEARCH,
                    "An error occurred while trying to perform an internal " +
                    "search to retrieve the user entry associated with the " +
                    "SASL DIGEST-MD5 username %s.  The result of that " +
                    "search was %s with a message of %s.");
    registerMessage(MSGID_SASLDIGESTMD5_MULTIPLE_MATCHING_ENTRIES,
                    "The internal search attempting to resolve SASL " +
                    "DIGEST-MD5 username %s matched multiple entries.  " +
                    "Authentication cannot succeed unless the username is " +
                    "mapped to exactly one user entry.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_MATCHING_ENTRIES,
                    "The server was not able to find any user entries for " +
                    "the provided username of %s.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_PW_ATTR,
                    "The SASL DIGEST-MD5 authentication failed because the " +
                    "mapped user entry did not contain any values for the %s " +
                    "attribute.");
    registerMessage(MSGID_SASLDIGESTMD5_UNKNOWN_STORAGE_SCHEME,
                    "A password in the target user entry %s could not be " +
                    "processed via SASL DIGEST-MD5 because that password has " +
                    "an unknown storage scheme of %s.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GET_CLEAR_PASSWORD,
                    "An error occurred while attempting to obtain the " +
                    "clear-text password for user %s from the value with " +
                    "storage scheme %s:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_CREDENTIALS,
                    "The DIGEST-MD5 credentials provided by the client are " +
                    "not appropriate for any password in the associated user " +
                    "account.");
    registerMessage(MSGID_SASLDIGESTMD5_NO_REVERSIBLE_PASSWORDS,
                    "SASL DIGEST-MD5 authentication is not possible for user " +
                    "%s because none of the passwords in the user entry are " +
                    "stored in a reversible form.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GENERATE_RESPONSE_DIGEST,
                    "An error occurred while attempting to generate a " +
                    "server-side digest to compare with the client " +
                    "response:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_CANNOT_GENERATE_RESPONSE_AUTH_DIGEST,
                    "An error occurred while trying to generate the " +
                    "response auth digest to include in the server SASL " +
                    "credentials:  %s.");
    registerMessage(MSGID_SASLDIGESTMD5_INVALID_CLOSING_QUOTE_POS,
                    "The DIGEST-MD5 response challenge could not be parsed " +
                    "because it had an invalid quotation mark at position %d.");
    registerMessage(MSGID_SASLDIGESTMD5_UPDATED_IDENTITY_MAPPER,
                    "Attribute " + ATTR_IDMAPPER_DN +
                    " in configuration entry %s has been updated.  The " +
                    "identity mapper defined in configuration entry %s " +
                    "will now be used to map usernames to entries when " +
                    "processing SASL DIGEST-MD5 bind requests.");
    registerMessage(MSGID_SASLDIGESTMD5_UPDATED_USERNAME_ATTR,
                    "Attribute " + ATTR_USERNAME_ATTRIBUTE +
                    " in configuration entry %s has been updated.  The %s " +
                    "attribute will now be used when looking up user entries " +
                    "based on their username.");
    registerMessage(MSGID_SASLDIGESTMD5_UPDATED_USER_BASE_DN,
                    "Attribute " + ATTR_USER_BASE_DN +
                    " in configuration entry %s has been updated.  The DN %s " +
                    "will now be used as the search base when looking up " +
                    "user entries based on their username.");
    registerMessage(MSGID_SASLDIGESTMD5_UPDATED_NEW_REALM,
                    "Attribute " + ATTR_DIGESTMD5_REALM +
                    " in configuration entry %s has been updated.  The realm " +
                    "\"%s\" will now be advertised by the server in the " +
                    "challenge response.");
    registerMessage(MSGID_SASLDIGESTMD5_UPDATED_NO_REALM,
                    "Attribute " + ATTR_DIGESTMD5_REALM +
                    " in configuration entry %s has been updated.  The " +
                    "realm(s) advertised by the server in the challenge " +
                    "response will be the DNs of the server suffixes.");


    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_USERNAME_ATTRIBUTE,
                    "Specifies the name of the attribute that will be used " +
                    "to identify user entries based on the username provided " +
                    "during SASL GSSAPI authentication.  This must " +
                    "specify the name of a valid attribute type defined in " +
                    "the server schema.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_USERNAME_ATTR,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USERNAME_ATTRIBUTE +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLGSSAPI_UNKNOWN_USERNAME_ATTR,
                    "The attribute %s referenced in configuration attribute " +
                    ATTR_USERNAME_ATTRIBUTE + " in configuration entry %s " +
                    "does not exist in the Directory Server schema.  The " +
                    "attribute that is to be used for username lookups " +
                    "during SASL GSSAPI authentication must be defined " +
                    "in the server schema.");
    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_USER_BASE_DN,
                    "Specifies the base DN that should be used when " +
                    "searching for entries based on the username provided " +
                    "during SASL GSSAPI authentication.  Changes to this " +
                    "configuration attribute will take effect immediately.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_USER_BASE_DN,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_USER_BASE_DN +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_IDENTITY_MAPPER_DN,
                    "Specifies the DN of the configuration entry that holds " +
                    "the configuration for the identity mapper that should " +
                    "be used to map the GSSAPI principal to a Directory " +
                    "Server user entry.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLGSSAPI_NO_IDENTITY_MAPPER_ATTR,
                    "Configuration entry %s does not contain attribute " +
                    ATTR_IDMAPPER_DN + " which specifies the DN of the " +
                    "identity mapper to use in conjunction with the GSSAPI " +
                    "SASL mechanism.  This is a required attribute.");
    registerMessage(MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER,
                    "The identity mapper %s specified in attribute " +
                    ATTR_IDMAPPER_DN + " of configuration entry %s does not " +
                    "reference a valid identity mapper configuration that is " +
                    "enabled for use in the Directory Server.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_IDENTITY_MAPPER,
                    "An error occurred while trying to process the value " +
                    "of the " + ATTR_IDMAPPER_DN + " attribute in " +
                    "configuration entry %s to determine which identity " +
                    "mapper should be used in conjunction with the GSSAPI " +
                    "SASL mechanism:  %s.");
    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_SERVER_FQDN,
                    "Specifies the fully-qualified domain name that should " +
                    "be used for the server during SASL GSSAPI " +
                    "authentication.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_SERVER_FQDN +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_USERNAME_ATTR,
                    "Attribute " + ATTR_USERNAME_ATTRIBUTE +
                    " in configuration entry %s has been updated.  The %s " +
                    "attribute will now be used when looking up user entries " +
                    "based on their username.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_USER_BASE_DN,
                    "Attribute " + ATTR_USER_BASE_DN +
                    " in configuration entry %s has been updated.  The DN %s " +
                    "will now be used as the search base when looking up " +
                    "user entries based on their username.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_IDENTITY_MAPPER,
                    "Attribute " + ATTR_IDMAPPER_DN +
                    " in configuration entry %s has been updated.  The value " +
                    "\"%s\" will now be used as the DN of the identity " +
                    "mapper configuration entry for GSSAPI authentication.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_NEW_SERVER_FQDN,
                    "Attribute " + ATTR_SERVER_FQDN +
                    " in configuration entry %s has been updated.  The value " +
                    "\"%s\" will now be used as the fully-qualified name of " +
                    "the Directory Server for GSSAPI authentication.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_NO_SERVER_FQDN,
                    "Attribute " + ATTR_SERVER_FQDN +
                    " in configuration entry %s has been updated.  The " +
                    "Directory Server will attempt to determine its own " +
                    "FQDN for use in GSSAPI authentication.");
    registerMessage(MSGID_SASLGSSAPI_UNEXPECTED_CALLBACK,
                    "An unexpected callback was provided for the SASL server " +
                    "for use during GSSAPI authentication:  %s.");
    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_KDC_ADDRESS,
                    "Specifies the address of the KDC that should be used " +
                    "during SASL GSSAPI authentication.  If this is not " +
                    "specified, then an attempt will be made to obtain it " +
                    "from the system-wide Kerberos configuration.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately for subsequent GSSAPI bind attempts.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_KDC_ADDRESS,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_GSSAPI_KDC +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_REALM,
                    "Specifies the default realm that should be used during " +
                    "SASL GSSAPI authentication.  If this is not specified, " +
                    "then an attempt will be made to obtain it from the " +
                    "system-wide Kerberos configuration.  Changes to this " +
                    "configuration attribute will take effect immediately " +
                    "for subsequent GSSAPI bind attempts.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_REALM,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_GSSAPI_REALM +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLGSSAPI_NO_CLIENT_CONNECTION,
                    "No client connection was available for use in " +
                    "processing the GSSAPI bind request.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_CREATE_SASL_SERVER,
                    "An error occurred while attempting to create the " +
                    "SASL server instance to process the GSSAPI bind " +
                    "request:  %s.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_EVALUATE_RESPONSE,
                    "An error occurred while attempting to evaluate the " +
                    "challenge response provided by the client in the " +
                    "GSSAPI bind request:  %s.");
    registerMessage(MSGID_SASLGSSAPI_NO_AUTHZ_ID,
                    "The GSSAPI authentication process appears to have " +
                    "completed but no authorization ID is available for " +
                    "mapping to a directory user.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_PERFORM_INTERNAL_SEARCH,
                    "An error occurred while attempting to perform an " +
                    "internal search to map the GSSAPI authorization ID %s " +
                    "to a Directory Server user (result code %d, error " +
                    "message \"%s\").");
    registerMessage(MSGID_SASLGSSAPI_MULTIPLE_MATCHING_ENTRIES,
                    "The GSSAPI authorization ID %s appears to have multiple " +
                    "matches in the Directory Server.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_MAP_AUTHZID,
                    "The GSSAPI authorization ID %s could not be mapped to " +
                    "any user in the Directory Server.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_KDC,
                    "Attribute " + ATTR_GSSAPI_KDC +
                    " in configuration entry %s has been updated.  The value " +
                    "\"%s\" will now be used as the address of the KDC for " +
                    "GSSAPI authentication.");
    registerMessage(MSGID_SASLGSSAPI_UNSET_KDC,
                    "Attribute " + ATTR_GSSAPI_KDC +
                    " in configuration entry %s has been un-set as a system " +
                    "property.  Any further GSSAPI authentication attempts " +
                    "will rely on the Kerberos configuration in the " +
                    "underlying operating system to determine the KDC " +
                    "address.");
    registerMessage(MSGID_SASLGSSAPI_UPDATED_REALM,
                    "Attribute " + ATTR_GSSAPI_REALM +
                    " in configuration entry %s has been updated.  The value " +
                    "\"%s\" will now be used as the default realm for GSSAPI " +
                    "authentication.");
    registerMessage(MSGID_SASLGSSAPI_UNSET_REALM,
                    "Attribute " + ATTR_GSSAPI_REALM +
                    " in configuration entry %s has been un-set as a system " +
                    "property.  Any further GSSAPI authentication attempts " +
                    "will rely on the Kerberos configuration in the " +
                    "underlying operating system to determine the default " +
                    "realm.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_CREATE_LOGIN_CONTEXT,
                    "An error occurred while attempting to create the JAAS " +
                    "login context for GSSAPI authentication:  %s.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_AUTHENTICATE_SERVER,
                    "An error occurred while attempting to perform " +
                    "server-side Kerberos authentication to support a GSSAPI " +
                    "bind operation:  %s.");
    registerMessage(MSGID_SASLGSSAPI_DESCRIPTION_KEYTAB_FILE,
                    "Specifies the path to the keytab file containing the " +
                    "secret key for the Kerberos principal to use when " +
                    "processing GSSAPI authentication.  If this is not " +
                    "specified, then the system-wide default keytab file " +
                    "will be used.  Changes to this configuration attribute " +
                    "will not take effect until the GSSAPI SASL mechanism " +
                    "handler is disabled and re-enabled or the Directory " +
                    "Server is restarted.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_GET_KEYTAB_FILE,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_GSSAPI_KEYTAB_FILE +
                    " attribute in configuration entry %s:  %s.");
    registerMessage(MSGID_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG,
                    "An error occurred while attempting to write a " +
                    "temporary JAAS configuration file for use during " +
                    "GSSAPI processing:  %s.");
    registerMessage(MSGID_SASLGSSAPI_DIFFERENT_AUTHID_AND_AUTHZID,
                    "The authentication ID %s was not equal to the " +
                    "authorization ID %s.  This is not supported for " +
                    "GSSAPI authentication.");


    registerMessage(MSGID_EXTOP_WHOAMI_NO_CLIENT_CONNECTION,
                    "No client connection structure is available for use in " +
                    "determining the requested authorization ID.");


    registerMessage(MSGID_SOFTREFCACHE_DESCRIPTION_LOCK_TIMEOUT,
                    "Specifies the maximum length of time in milliseconds " +
                    "that the entry cache should block while attempting " +
                    "to acquire a lock for an entry.  Changes to this " +
                    "configuration attribute will take effect immediately.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DETERMINE_LOCK_TIMEOUT,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_SOFTREFCACHE_LOCK_TIMEOUT +
                    " attribute in configuration entry %s:  %s.  The default " +
                    "of %d will be used.");
    registerMessage(MSGID_SOFTREFCACHE_DESCRIPTION_INCLUDE_FILTERS,
                    "Specifies a set of search filters that may be used to " +
                    "indicate which entries should be included in the entry " +
                    "cache.  Entries that do not match at least one of these " +
                    "filters will not be stored in the cache.  If no filters " +
                    "are provided, then any entry will be accepted.  Changes " +
                    "to this configuration attribute will take effect " +
                    "immediately, but will not impact existing entries that " +
                    "are already held in the cache.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DECODE_INCLUDE_FILTER,
                    "An error occurred while attempting to decode the value " +
                    "\"%s\" from attribute " +
                    ATTR_SOFTREFCACHE_INCLUDE_FILTER + " of entry %s:  %s.  " +
                    "This filter will not be used when determining whether " +
                    "to store an entry in the cache.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_INCLUDE_FILTERS,
                    "An error occurred while attempting to decode any of the " +
                    "values from attribute " +
                    ATTR_SOFTREFCACHE_INCLUDE_FILTER + " of entry %s.  All " +
                    "entries will be considered eligible for inclusion in " +
                    "the cache.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DETERMINE_INCLUDE_FILTERS,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_SOFTREFCACHE_INCLUDE_FILTER +
                    " attribute in configuration entry %s:  %s.  All entries " +
                    "will be considered eligible for inclusion in the cache.");
    registerMessage(MSGID_SOFTREFCACHE_DESCRIPTION_EXCLUDE_FILTERS,
                    "Specifies a set of search filters that may be used to " +
                    "indicate which entries should be excluded from the " +
                    "entry cache.  Entries that match any of these filters " +
                    "will not be stored in the cache.  If no filters are " +
                    "provided, then any entry will be accepted.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately, but will not impact existing entries that " +
                    "are already held in the cache.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DECODE_EXCLUDE_FILTER,
                    "An error occurred while attempting to decode the value " +
                    "\"%s\" from attribute " +
                    ATTR_SOFTREFCACHE_EXCLUDE_FILTER + " of entry %s:  %s.  " +
                    "This filter will not be used when determining whether " +
                    "to store an entry in the cache.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DECODE_ANY_EXCLUDE_FILTERS,
                    "An error occurred while attempting to decode any of the " +
                    "values from attribute " +
                    ATTR_SOFTREFCACHE_EXCLUDE_FILTER + " of entry %s.  All " +
                    "entries will be considered eligible for inclusion in " +
                    "the cache.");
    registerMessage(MSGID_SOFTREFCACHE_CANNOT_DETERMINE_EXCLUDE_FILTERS,
                    "An error occurred while attempting to determine the " +
                    "value of the " + ATTR_SOFTREFCACHE_EXCLUDE_FILTER +
                    " attribute in configuration entry %s:  %s.  All entries " +
                    "will be considered eligible for inclusion in the cache.");
    registerMessage(MSGID_SOFTREFCACHE_INVALID_LOCK_TIMEOUT,
                    "The " + ATTR_SOFTREFCACHE_LOCK_TIMEOUT + " attribute of " +
                    "entry %s, which specifies the maximum length of time in " +
                    "milliseconds that the cache should block while " +
                    "attempting to obtain a lock on an entry, has an invalid " +
                    "value:  %s.  Its value must be a positive integer, or " +
                    "zero to indicate that it should never block.");
    registerMessage(MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTER,
                    "The " + ATTR_SOFTREFCACHE_INCLUDE_FILTER + " attribute " +
                    "of entry %s, which specifies a set of search filters " +
                    "that may be used to control which entries are included " +
                    "in the cache, has an invalid value of \"%s\":  %s.");
    registerMessage(MSGID_SOFTREFCACHE_INVALID_INCLUDE_FILTERS,
                    "The " + ATTR_SOFTREFCACHE_INCLUDE_FILTER + " attribute " +
                    "of entry %s, which specifies a set of search filters " +
                    "that may be used to control which entries are included " +
                    "in the cache, has an invalid value:  %s.");
    registerMessage(MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTER,
                    "The " + ATTR_SOFTREFCACHE_EXCLUDE_FILTER + " attribute " +
                    "of entry %s, which specifies a set of search filters " +
                    "that may be used to control which entries are excluded " +
                    "from the cache, has an invalid value of \"%s\":  %s.");
    registerMessage(MSGID_SOFTREFCACHE_INVALID_EXCLUDE_FILTERS,
                    "The " + ATTR_SOFTREFCACHE_EXCLUDE_FILTER + " attribute " +
                    "of entry %s, which specifies a set of search filters " +
                    "that may be used to control which entries are excluded " +
                    "from the cache, has an invalid value:  %s.");
    registerMessage(MSGID_SOFTREFCACHE_UPDATED_LOCK_TIMEOUT,
                    "The lock timeout that will be used to determine the " +
                    "length of time that the cache should block while " +
                    "attempting to acquire a lock for an entry has been " +
                    "set to %d milliseconds.");
    registerMessage(MSGID_SOFTREFCACHE_UPDATED_INCLUDE_FILTERS,
                    "The set of search filters that will control which " +
                    "entries may be included in the cache has been updated.");
    registerMessage(MSGID_SOFTREFCACHE_UPDATED_INCLUDE_FILTERS,
                    "The set of search filters that will control which " +
                    "entries should be be excluded from the cache has been " +
                    "updated.");


    registerMessage(MSGID_EXACTMAP_DESCRIPTION_MATCH_ATTR,
                    "Specifies the name or OID of the attribute whose value " +
                    "should exactly match the ID string provided to this " +
                    "identity mapper.  At least one value must be provided.  " +
                    "All values must refer to the name or OID of an " +
                    "attribute type defined in the Directory Server schema.  " +
                    "If multiple attribute type names or OIDs are provided, " +
                    "then at least one of those attributes must contain the " +
                    "provided ID string value in exactly one entry.");
    registerMessage(MSGID_EXACTMAP_NO_MATCH_ATTR,
                    "Configuration entry %s does not have any values for " +
                    "attribute " + ATTR_MATCH_ATTRIBUTE + ", which is used " +
                    "to specify which attribute(s) may be used to map a " +
                    "given ID string to a user entry.");
    registerMessage(MSGID_EXACTMAP_UNKNOWN_ATTR,
                    "Configuration entry %s contains value %s for attribute " +
                    ATTR_MATCH_ATTRIBUTE + " but that is not a valid name or " +
                    "OID for any attribute type defined in the Directory " +
                    "Server schema.");
    registerMessage(MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_ATTR,
                    "An error occurred while attempting to process the " +
                    "value(s) of attribute " + ATTR_MATCH_ATTRIBUTE +
                    " in configuration entry %s:  %s.");
    registerMessage(MSGID_EXACTMAP_DESCRIPTION_SEARCH_BASE,
                    "Specifies the base DN(s) that should be used when " +
                    "performing searches to map the provided ID string to a " +
                    "user entry.  If no values are provided, then the " +
                    "root DSE will be used as the search base.");
    registerMessage(MSGID_EXACTMAP_CANNOT_DETERMINE_MATCH_BASE,
                    "An error occurred while attempting to process the " +
                    "value(s) of attribute " + ATTR_MATCH_BASE +
                    " in configuration entry %s:  %s.");
    registerMessage(MSGID_EXACTMAP_UPDATED_MATCH_ATTRS,
                    "The set of attributes to use when matching ID strings " +
                    "to user entries contained in attribute " +
                    ATTR_MATCH_ATTRIBUTE + " of configuration entry %s has " +
                    "been updated.");
    registerMessage(MSGID_EXACTMAP_UPDATED_MATCH_BASES,
                    "The set of search base DNs to use when matching ID " +
                    "strings to user entries contained in attribute " +
                    ATTR_MATCH_BASE + " of configuration entry %s has been " +
                    "updated.");
    registerMessage(MSGID_EXACTMAP_MULTIPLE_MATCHING_ENTRIES,
                    "ID string %s mapped to multiple users.");
    registerMessage(MSGID_EXACTMAP_INEFFICIENT_SEARCH,
                    "The internal search based on ID string %s could not be " +
                    "processed efficiently:  %s.  Check the server " +
                    "configuration to ensure that all associated backends " +
                    "are properly configured for these types of searches.");
    registerMessage(MSGID_EXACTMAP_SEARCH_FAILED,
                    "An internal failure occurred while attempting to " +
                    "resolve ID string %s to a user entry:  %s.");


    registerMessage(MSGID_EXTOP_CANCEL_NO_REQUEST_VALUE,
                    "Unable to process the cancel request because the " +
                    "extended operation did not include a request value.");
    registerMessage(MSGID_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE,
                    "An error occurred while attempting to decode the value " +
                    "of the cancel extended request:  %s.");
    registerMessage(MSGID_EXTOP_CANCEL_REASON,
                    "Processing on this operation was terminated as a " +
                    "result of receiving a cancel request (message ID %d).");


    registerMessage(MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MIN_LENGTH,
                    "Specifies the minimum number of characters that a " +
                    "password will be allowed to have.  A value of zero " +
                    "indicates that there is no minimum length.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MIN_LENGTH,
                    "An error occurred while attempting to determine the " +
                    "minimum allowed password length from the " +
                    ATTR_PASSWORD_MIN_LENGTH + " attribute:  %s.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_DESCRIPTION_MAX_LENGTH,
                    "Specifies the maximum number of characters that a " +
                    "password will be allowed to have.  A value of zero " +
                    "indicates that there is no maximum length.  Changes to " +
                    "this configuration attribute will take effect " +
                    "immediately.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_CANNOT_DETERMINE_MAX_LENGTH,
                    "An error occurred while attempting to determine the " +
                    "maximum allowed password length from the " +
                    ATTR_PASSWORD_MAX_LENGTH + " attribute:  %s.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_MIN_GREATER_THAN_MAX,
                    "The configured minimum password length of %d characters " +
                    "is greater than the configured maximum password length " +
                    "of %d.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_TOO_SHORT,
                    "The provided password is shorter than the minimum " +
                    "required length of %d characters.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_TOO_LONG,
                    "The provided password is longer than the maximum " +
                    "allowed length of %d characters.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_UPDATED_MIN_LENGTH,
                    "The minimum password length has been updated to %d.");
    registerMessage(MSGID_PWLENGTHVALIDATOR_UPDATED_MAX_LENGTH,
                    "The maximum password length has been updated to %d.");


    registerMessage(MSGID_RANDOMPWGEN_DESCRIPTION_CHARSET,
                    "Specifies the character set(s) that should be used to " +
                    "generate the passwords.  Each character set should " +
                    "be given a name (consisting of only ASCII alphabetic " +
                    "characters) followed immediately by a colon and the set " +
                    "of characters that should be included in that " +
                    "character set.  Changes to this configuration attribute " +
                    "will take effect immediately.");
    registerMessage(MSGID_RANDOMPWGEN_NO_CHARSETS,
                    "Configuration entry \"%s\" does not contain attribute " +
                    ATTR_PASSWORD_CHARSET + " which specifies the sets of " +
                    "characters that should be used when generating the " +
                    "password.  This is a required attribute.");
    registerMessage(MSGID_RANDOMPWGEN_CHARSET_NAME_CONFLICT,
                    "Configuration entry \"%s\" contains multiple " +
                    "definitions for the %s character set.");
    registerMessage(MSGID_RANDOMPWGEN_CANNOT_DETERMINE_CHARSETS,
                    "An error occurred while attempting to decode the " +
                    "value(s) of the configuration attribute " +
                    ATTR_PASSWORD_CHARSET + ", which is used to hold the " +
                    "character set(s) for use in generating the password:  " +
                    "%s.");
    registerMessage(MSGID_RANDOMPWGEN_DESCRIPTION_PWFORMAT,
                    "Specifies the format that should be used for passwords " +
                    "constructed by this password generator.  The value " +
                    "should be a comma-delimited sequence of elements, where " +
                    "each element is the name of a character set followed " +
                    "by a colon and the number of characters to choose at " +
                    "random from that character set.  Changes to this " +
                    "configuration attribute will take effect immediately.");
    registerMessage(MSGID_RANDOMPWGEN_NO_PWFORMAT,
                    "Configuration entry \"%s\" does not contain attribute " +
                    ATTR_PASSWORD_FORMAT + " which specifies the format to " +
                    "use for the generated password.  This is a required " +
                    "attribute.");
    registerMessage(MSGID_RANDOMPWGEN_UNKNOWN_CHARSET,
                    "The password format string \"%s\" references an " +
                    "undefined character set \"%s\".");
    registerMessage(MSGID_RANDOMPWGEN_INVALID_PWFORMAT,
                    "The password format string \"%s\" contains an invalid " +
                    "syntax.  This value should be a comma-delimited " +
                    "sequence of elements, where each element is the name of " +
                    "a character set followed by a colon and the number of " +
                    "characters to choose at random from that character set.");
    registerMessage(MSGID_RANDOMPWGEN_CANNOT_DETERMINE_PWFORMAT,
                    "An error occurred while attempting to decode the " +
                    "value for configuration attribute " +
                    ATTR_PASSWORD_FORMAT + ", which is used to specify the " +
                    "format for the generated passwords:  %s.");
  }
}

