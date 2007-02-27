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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.messages;



import org.opends.server.extensions.ConfigFileHandler;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the tools.
 */
public class ToolMessages
{
  /**
   * The message ID for the message that will be used if an SSL connection
   * could not be created to the server.
   */
  public static final int MSGID_TOOLS_CANNOT_CREATE_SSL_CONNECTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID for the message that will be used if an SSL connection
   * could not be created to the server because the connection factory was
   * not initialized.
   */
  public static final int MSGID_TOOLS_SSL_CONNECTION_NOT_INITIALIZED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 2;



  /**
   * The message ID for the message that will be used if the SSL keystore
   * could not be loaded.
   */
  public static final int MSGID_TOOLS_CANNOT_LOAD_KEYSTORE_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 3;


  /**
   * The message ID for the message that will be used if the key manager
   * could not be initialized.
   */
  public static final int MSGID_TOOLS_CANNOT_INIT_KEYMANAGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 4;

  /**
   * The message ID for the message that will be used if the SSL trust store
   * could not be loaded.
   */
  public static final int MSGID_TOOLS_CANNOT_LOAD_TRUSTSTORE_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 5;


  /**
   * The message ID for the message that will be used if the trust manager
   * could not be initialized.
   */
  public static final int MSGID_TOOLS_CANNOT_INIT_TRUSTMANAGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 6;



  /**
   * The message ID for the message that will be used as the description of the
   * listSchemes argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_LISTSCHEMES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 7;



  /**
   * The message ID for the message that will be used as the description of the
   * clearPassword argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_CLEAR_PW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 8;



  /**
   * The message ID for the message that will be used as the description of the
   * clearPasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_CLEAR_PW_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 9;



  /**
   * The message ID for the message that will be used as the description of the
   * encodedPassword argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_ENCODED_PW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 10;



  /**
   * The message ID for the message that will be used as the description of the
   * encodedPasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_ENCODED_PW_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 11;



  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 12;



  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 13;



  /**
   * The message ID for the message that will be used as the description of the
   * storageScheme argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_SCHEME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 14;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 15;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_ENCPW_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 16;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_ENCPW_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 17;



  /**
   * The message ID for the message that will be used if no clear-text password
   * was provided in the arguments.  This takes two arguments, which are the
   * long identifier for the clear password argument and the long identifier for
   * the clear password file argument.
   */
  public static final int MSGID_ENCPW_NO_CLEAR_PW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 18;



  /**
   * The message ID for the message that will be used if no password storage
   * scheme was provided in the arguments.  This takes a single argument, which
   * is the long identifier for the password storage scheme argument.
   */
  public static final int MSGID_ENCPW_NO_SCHEME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 19;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_ENCPW_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 20;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_ENCPW_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 21;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_ENCPW_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 22;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the core Directory Server configuration.  This takes a
   * single argument, which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_ENCPW_CANNOT_INITIALIZE_CORE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 23;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the Directory Server password storage schemes.  This
   * takes a single argument, which is a message with information about the
   * problem that occurred.
   */
  public static final int MSGID_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 24;



  /**
   * The message ID for the message that will be used if no storage schemes have
   * been defined in the Directory Server.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_NO_STORAGE_SCHEMES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 25;



  /**
   * The message ID for the message that will be used if the requested storage
   * scheme is not configured for use in the Directory Server.  This takes a
   * single argument, which is the name of the requested storage scheme.
   */
  public static final int MSGID_ENCPW_NO_SUCH_SCHEME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 26;



  /**
   * The message ID for the message that will be used if the clear-text and
   * encoded passwords match.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_PASSWORDS_MATCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 27;



  /**
   * The message ID for the message that will be used if the clear-text and
   * encoded passwords do not match.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_PASSWORDS_DO_NOT_MATCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 28;



  /**
   * The message ID for the message that will be used to display the encoded
   * password.  This takes a single argument, which is the encoded password.
   */
  public static final int MSGID_ENCPW_ENCODED_PASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 29;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to encode the clear-text password.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_ENCPW_CANNOT_ENCODE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 30;



  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 31;



  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 32;



  /**
   * The message ID for the message that will be used as the description of the
   * ldifFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_LDIF_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 33;



  /**
   * The message ID for the message that will be used as the description of the
   * appendToLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_APPEND_TO_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 34;



  /**
   * The message ID for the message that will be used as the description of the
   * backendID argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 35;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeBranch argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_BRANCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 36;



  /**
   * The message ID for the message that will be used as the description of the
   * includeAttribute argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_ATTRIBUTE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 37;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeAttribute argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 38;



  /**
   * The message ID for the message that will be used as the description of the
   * includeFilter argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 39;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeFilter argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 40;



  /**
   * The message ID for the message that will be used as the description of the
   * wrapColumn argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_WRAP_COLUMN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 41;



  /**
   * The message ID for the message that will be used as the description of the
   * compressLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_COMPRESS_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 42;



  /**
   * The message ID for the message that will be used as the description of the
   * encryptLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_ENCRYPT_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 43;



  /**
   * The message ID for the message that will be used as the description of the
   * signHash argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_SIGN_HASH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 44;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 45;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 46;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 47;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDIFEXPORT_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 48;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 49;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 50;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the core Directory Server configuration.  This takes a
   * single argument, which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CORE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 51;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an exclude filter.  This takes two arguments, which are
   * the provided filter string and a message explaining the problem that was
   * encountered.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 52;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an include filter.  This takes two arguments, which are
   * the provided filter string and a message explaining the problem that was
   * encountered.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 53;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the base DN string.  This takes two arguments, which are
   * the provided base DN string and a message explaining the problem that
   * was encountered.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DECODE_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 54;



  /**
   * The message ID for the message that will be used if multiple backends claim
   * to have the requested backend ID.  This takes a single argument, which is
   * the requested backend ID.
   */
  public static final int MSGID_LDIFEXPORT_MULTIPLE_BACKENDS_FOR_ID=
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 55;



  /**
   * The message ID for the message that will be used if no backends claim to
   * have the requested backend ID.  This takes a single argument, which is the
   * requested backend ID.
   */
  public static final int MSGID_LDIFEXPORT_NO_BACKENDS_FOR_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 56;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the exclude branch string.  This takes two arguments,
   * which are the provided base DN string and a message explaining the problem
   * that was encountered.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 57;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the wrap column as an integer.  This takes a single
   * argument, which is the string representation of the wrap column.
   */
  public static final int
       MSGID_LDIFEXPORT_CANNOT_DECODE_WRAP_COLUMN_AS_INTEGER =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 58;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform the export.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_ERROR_DURING_EXPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 59;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the backend configuration base DN string.  This takes two
   * arguments, which are the backend config base DN string and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DECODE_BACKEND_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 60;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the backend configuration base entry.  This takes two
   * arguments, which are the DN of the entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 61;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend class name.  This takes two arguments,
   * which are the DN of the backend configuration entry and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 62;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for a Directory Server backend.  This takes three
   * arguments, which are the class name, the DN of the configuration entry, and
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_LOAD_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a class for a Directory Server backend.  This takes
   * three arguments, which are the class name, the DN of the configuration
   * entry, and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_INSTANTIATE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 64;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not define any base DNs.  This takes a single argument, which is
   * the DN of the backend configuration entry.
   */
  public static final int MSGID_LDIFEXPORT_NO_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 65;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of base DNs for a backend.  This takes two
   * arguments, which are the DN of the backend configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DETERMINE_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 66;



  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 67;



  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 68;



  /**
   * The message ID for the message that will be used as the description of the
   * ldifFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_LDIF_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 69;



  /**
   * The message ID for the message that will be used as the description of the
   * append argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_APPEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 70;



  /**
   * The message ID for the message that will be used as the description of the
   * replaceExisting argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_REPLACE_EXISTING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 71;



  /**
   * The message ID for the message that will be used as the description of the
   * backendID argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 72;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeBranch argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_EXCLUDE_BRANCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 73;



  /**
   * The message ID for the message that will be used as the description of the
   * includeAttribute argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_INCLUDE_ATTRIBUTE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 74;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeAttribute argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 75;



  /**
   * The message ID for the message that will be used as the description of the
   * includeFilter argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_INCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 76;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeFilter argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_EXCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 77;



  /**
   * The message ID for the message that will be used as the description of the
   * rejectFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_REJECT_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 78;



  /**
   * The message ID for the message that will be used as the description of the
   * overwriteRejects argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_OVERWRITE_REJECTS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 79;



  /**
   * The message ID for the message that will be used as the description of the
   * isCompressed argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_IS_COMPRESSED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 80;



  /**
   * The message ID for the message that will be used as the description of the
   * isEncrypted argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_IS_ENCRYPTED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 81;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 82;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 83;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 84;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDIFIMPORT_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 85;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 86;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 87;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the core Directory Server configuration.  This takes a
   * single argument, which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CORE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 88;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an exclude filter.  This takes two arguments, which are
   * the provided filter string and a message explaining the problem that was
   * encountered.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 89;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode an include filter.  This takes two arguments, which are
   * the provided filter string and a message explaining the problem that was
   * encountered.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 90;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the base DN string.  This takes two arguments, which are
   * the provided base DN string and a message explaining the problem that
   * was encountered.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DECODE_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 91;



  /**
   * The message ID for the message that will be used if multiple backends claim
   * to have the requested backend ID.  This takes a single argument, which is
   * the requested base DN.
   */
  public static final int MSGID_LDIFIMPORT_MULTIPLE_BACKENDS_FOR_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 92;



  /**
   * The message ID for the message that will be used if no backends claim to
   * have the requested backend ID.  This takes a single argument, which is the
   * requested base DN.
   */
  public static final int MSGID_LDIFIMPORT_NO_BACKENDS_FOR_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 93;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the exclude branch string.  This takes two arguments,
   * which are the provided base DN string and a message explaining the problem
   * that was encountered.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 94;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the rejects file.  This takes two arguments, which are the
   * path to the rejects file and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 95;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform the import.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_ERROR_DURING_IMPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 96;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the backend configuration base DN string.  This takes two
   * arguments, which are the backend config base DN string and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DECODE_BACKEND_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 97;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the backend configuration base entry.  This takes two
   * arguments, which are the DN of the entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 98;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend class name.  This takes two arguments,
   * which are the DN of the backend configuration entry and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 99;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for a Directory Server backend.  This takes three
   * arguments, which are the class name, the DN of the configuration entry, and
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_LOAD_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 100;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a class for a Directory Server backend.  This takes
   * three arguments, which are the class name, the DN of the configuration
   * entry, and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_INSTANTIATE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 101;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not define any base DNs.  This takes a single argument, which is
   * the DN of the backend configuration entry.
   */
  public static final int MSGID_LDIFIMPORT_NO_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 102;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of base DNs for a backend.  This takes two
   * arguments, which are the DN of the backend configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DETERMINE_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 103;


  /**
   * The message ID for the message that will be give information
   * on what operation is being processed.
   */
  public static final int MSGID_PROCESSING_OPERATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 104;


  /**
   * The message ID for the message that will be give information
   * on the failure of an operation.
   */
  public static final int MSGID_OPERATION_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 105;


  /**
   * The message ID for the message that will be give information
   * when an operation is successful.
   */
  public static final int MSGID_OPERATION_SUCCESSFUL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 106;

  /**
   * The message ID for the message that will be give information
   * when a compare operation is processed.
   */
  public static final int MSGID_PROCESSING_COMPARE_OPERATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 107;


  /**
   * The message ID for the message that will be give information
   * when a compare operation returns false.
   */
  public static final int MSGID_COMPARE_OPERATION_RESULT_FALSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 108;


  /**
   * The message ID for the message that will be give information
   * when a compare operation returns true.
   */
  public static final int MSGID_COMPARE_OPERATION_RESULT_TRUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 109;


  /**
   * The message ID for the message that will be give information
   * when an invalid protocol operation is returned in a
   * search result.
   */
  public static final int MSGID_SEARCH_OPERATION_INVALID_PROTOCOL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 110;



  /**
   * The message ID for the message that will be used as the description of the
   * trustAll argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_TRUSTALL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 111;


  /**
   * The message ID for the message that will be used as the description of the
   * bindDN argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_BINDDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 112;


  /**
   * The message ID for the message that will be used as the description of the
   * bindPassword argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_BINDPASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 113;


  /**
   * The message ID for the message that will be used as the description of the
   * bindPasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_BINDPASSWORDFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 114;


  /**
   * The message ID for the message that will be used as the description of the
   * encoding argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_ENCODING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 115;


  /**
   * The message ID for the message that will be used as the description of the
   * verbose argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_VERBOSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 116;


  /**
   * The message ID for the message that will be used as the description of the
   * keystorePath argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_KEYSTOREPATH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 117;


  /**
   * The message ID for the message that will be used as the description of the
   * trustStorePath argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_TRUSTSTOREPATH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 118;


  /**
   * The message ID for the message that will be used as the description of the
   * keystorePassword argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_KEYSTOREPASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 119;


  /**
   * The message ID for the message that will be used as the description of the
   * host argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_HOST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 120;


  /**
   * The message ID for the message that will be used as the description of the
   * port argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 121;


  /**
   * The message ID for the message that will be used as the description of the
   * showUsage argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_SHOWUSAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 122;


  /**
   * The message ID for the message that will be used as the description of the
   * controls argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_CONTROLS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 123;


  /**
   * The message ID for the message that will be used as the description of the
   * continueOnError argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_CONTINUE_ON_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 124;


  /**
   * The message ID for the message that will be used as the description of the
   * useSSL argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_USE_SSL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 125;


  /**
   * The message ID for the message that will be used as the description of the
   * startTLS argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_START_TLS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 126;


  /**
   * The message ID for the message that will be used as the description of the
   * useSASLExternal argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_USE_SASL_EXTERNAL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 127;


  /**
   * The message ID for the message that will be used as the description of the
   * filename argument.  This does not take any arguments.
   */
  public static final int MSGID_DELETE_DESCRIPTION_FILENAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 128;



  /**
   * The message ID for the message that will be used as the description of the
   * deleteSubtree argument.  This does not take any arguments.
   */
  public static final int MSGID_DELETE_DESCRIPTION_DELETE_SUBTREE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 129;


  /**
   * The message ID for the message that will be used as the description of the
   * defaultAdd argument.  This does not take any arguments.
   */
  public static final int MSGID_MODIFY_DESCRIPTION_DEFAULT_ADD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 130;


  /**
   * The message ID for the message that will be used as the description of the
   * baseDN argument.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_DESCRIPTION_BASEDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 131;


  /**
   * The message ID for the message that will be used as the description of the
   * sizeLimit argument.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_DESCRIPTION_SIZE_LIMIT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 132;


  /**
   * The message ID for the message that will be used as the description of the
   * timeLimit argument.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_DESCRIPTION_TIME_LIMIT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 133;


  /**
   * The message ID for the message that will be used as the description of the
   * searchScope argument.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_DESCRIPTION_SEARCH_SCOPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 134;


  /**
   * The message ID for the message that will be used as the description of the
   * dereferencePolicy argument.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_DESCRIPTION_DEREFERENCE_POLICY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 135;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to send a simple bind request to the Directory Server.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_SEND_SIMPLE_BIND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 136;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the bind response from the Directory Server.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 137;



  /**
   * The message ID for the message that will be used if the server sends a
   * notice of disconnection unsolicited response.  This takes two arguments,
   * which are the result code and error message from the notice of
   * disconnection response.
   */
  public static final int MSGID_LDAPAUTH_SERVER_DISCONNECT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 138;



  /**
   * The message ID for the message that will be used if the server sends an
   * unexpected extended response to the client.  This takes a single argument,
   * which is a string representation of the extended response that was
   * received.
   */
  public static final int MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 139;



  /**
   * The message ID for the message that will be used if the server sends an
   * unexpected response to the client.  This takes a single argument, which is
   * a string representation of the extended response that was received.
   */
  public static final int MSGID_LDAPAUTH_UNEXPECTED_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 140;



  /**
   * The message ID for the message that will be used if the simple bind attempt
   * fails.  This takes three arguments, which are the integer and string
   * representations of the result code and the error message from the bind
   * response.
   */
  public static final int MSGID_LDAPAUTH_SIMPLE_BIND_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 141;



  /**
   * The message ID for the message that will be used if an attempt was made to
   * process a SASL bind without specifying which SASL mechanism to use.  This
   * does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_NO_SASL_MECHANISM =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 142;



  /**
   * The message ID for the message that will be used if an unsupported SASL
   * mechanism was requested.  This takes a single argument, which is the
   * requested mechanism.
   */
  public static final int MSGID_LDAPAUTH_UNSUPPORTED_SASL_MECHANISM =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 143;



  /**
   * The message ID for the message that will be used if multiple values are
   * provided for the trace property when performing a SASL ANONYMOUS bind.
   * This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_TRACE_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 144;



  /**
   * The message ID for the message that will be used if an invalid SASL
   * property is provided.  This takes two arguments, which are the name of the
   * invalid property and the name of the SASL mechanism.
   */
  public static final int MSGID_LDAPAUTH_INVALID_SASL_PROPERTY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 145;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to send a SASL bind request to the Directory Server.  This takes two
   * arguments, which are the name of the SASL mechanism and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 146;



  /**
   * The message ID for the message that will be used if a SASL bind attempt
   * fails.  This takes four arguments, which are the SASL mechanism name,
   * integer and string representations of the result code and the error message
   * from the bind response.
   */
  public static final int MSGID_LDAPAUTH_SASL_BIND_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 147;



  /**
   * The message ID for the message that will be used if no SASL properties are
   * provided for a SASL mechanism that requires at least one such property.
   * This takes a single argument, which is the name of the SASL mechanism.
   */
  public static final int MSGID_LDAPAUTH_NO_SASL_PROPERTIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 148;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * specify multiple values for the authID property.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPAUTH_AUTHID_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 149;



  /**
   * The message ID for the message that will be used if no value is provided
   * for the required authID SASL property.  This takes a single argument, which
   * is the name of the SASL mechanism.
   */
  public static final int MSGID_LDAPAUTH_SASL_AUTHID_REQUIRED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 150;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to send the initial bind request in a multi-stage SASL bind.  This
   * takes two arguments, which are the name of the SASL mechanism and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_SEND_INITIAL_SASL_BIND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 151;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the initial bind response in a multi-stage SASL bind.  This
   * takes two arguments, which are the name of the SASL mechanism and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 152;



  /**
   * The message ID for the message that will be used if the initial response
   * message in a multi-stage SASL bind had a result code other than "SASL bind
   * in progress".  This takes four arguments, which are the name of the SASL
   * mechanism, integer and string representations of the result code, and the
   * error message from the bind response.
   */
  public static final int MSGID_LDAPAUTH_UNEXPECTED_INITIAL_BIND_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 153;



  /**
   * The message ID for the message that will be used if the initial CRAM-MD5
   * bind response does not include server SASL credentials.  This does not take
   * any arguments.
   */
  public static final int MSGID_LDAPAUTH_NO_CRAMMD5_SERVER_CREDENTIALS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 154;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the MD5 message digest.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_INITIALIZE_MD5_DIGEST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 155;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to send the second bind request in a multi-stage SASL bind.  This
   * takes two arguments, which are the name of the SASL mechanism and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_SEND_SECOND_SASL_BIND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 156;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the second bind response in a multi-stage SASL bind.  This
   * takes two arguments, which are the name of the SASL mechanism and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 157;



  /**
   * The message ID for the message that will be used if one or more SASL
   * properties were provided for a mechanism that does not support them.  This
   * takes a single argument, which is the name of the SASL mechanism.
   */
  public static final int MSGID_LDAPAUTH_NO_ALLOWED_SASL_PROPERTIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 158;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * specify multiple values for the authzID property.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPAUTH_AUTHZID_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 159;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * specify multiple values for the realm property.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPAUTH_REALM_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 160;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * specify multiple values for the QoP property.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPAUTH_QOP_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 161;



  /**
   * The message ID for the message that will be used if either the auth-int or
   * auth-conf QoP is requested for DIGEST-MD5 authentication.  This takes a
   * single value, which is the requested QoP.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_QOP_NOT_SUPPORTED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 162;



  /**
   * The message ID for the message that will be used if an invalid QoP mode is
   * requested.  This takes a single value, which is the requested QoP.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_INVALID_QOP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 163;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * specify multiple values for the digest-URI property.  This does not take
   * any arguments.
   */
  public static final int MSGID_LDAPAUTH_DIGEST_URI_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 164;



  /**
   * The message ID for the message that will be used if the initial DIGEST-MD5
   * bind response does not include server SASL credentials.  This does not take
   * any arguments.
   */
  public static final int MSGID_LDAPAUTH_NO_DIGESTMD5_SERVER_CREDENTIALS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 165;



  /**
   * The message ID for the message that will be used if the client cannot parse
   * the server SASL credentials because an invalid token was encountered.  This
   * takes two arguments, which are the invalid token and the position at which
   * it starts in the server SASL credentials.
   */
  public static final int
       MSGID_LDAPAUTH_DIGESTMD5_INVALID_TOKEN_IN_CREDENTIALS =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 166;



  /**
   * The message ID for the message that will be used if the server SASL
   * credentials includes an invalid character set name.  This takes a single
   * argument, which is the name of the invalid character set.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_INVALID_CHARSET =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 167;



  /**
   * The message ID for the message that will be used if the QoP mode that the
   * client intends to use is not supported by the server.  This takes two
   * arguments, which are the client's requested QoP mode and the server's list
   * of supported QoP modes.
   */
  public static final int MSGID_LDAPAUTH_REQUESTED_QOP_NOT_SUPPORTED_BY_SERVER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 168;



  /**
   * The message ID for the message that will be used if the server SASL
   * credentials for a DIGEST-MD5 bind do not include a nonce.  This does not
   * take any arguments.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_NO_NONCE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 169;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to generate the DIGEST-MD5 response digest.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int
       MSGID_LDAPAUTH_DIGESTMD5_CANNOT_CREATE_RESPONSE_DIGEST =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 170;



  /**
   * The message ID for the message that will be used if the server SASL
   * credentials for a DIGEST-MD5 second-stage bind response does not include
   * the rspauth element.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_NO_RSPAUTH_CREDS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 171;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the rspauth value provided by the server in the DIGEST-MD5
   * bind.  This takes a single argument, which is a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_COULD_NOT_DECODE_RSPAUTH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 172;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to calculate the rspauth value to compare with the version provided
   * by the server in the DIGEST-MD5 bind.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_COULD_NOT_CALCULATE_RSPAUTH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 173;



  /**
   * The message ID for the message that will be used if the server-provided
   * rspauth value differs from the value calculated by the client.  This does
   * not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_RSPAUTH_MISMATCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 174;



  /**
   * The message ID for the message that will be used if the server SASL
   * credentials contains a closing quotation mark in an unexpected location.
   * This takes a single argument, which is the position of the unexpected
   * quote.
   */
  public static final int MSGID_LDAPAUTH_DIGESTMD5_INVALID_CLOSING_QUOTE_POS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 175;



  /**
   * The message ID for the message that will be used as the description of the
   * trace SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_TRACE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 176;



  /**
   * The message ID for the message that will be used as the description of the
   * authID SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 177;



  /**
   * The message ID for the message that will be used as the description of the
   * realm SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_REALM =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 178;



  /**
   * The message ID for the message that will be used as the description of the
   * QoP SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_QOP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 179;



  /**
   * The message ID for the message that will be used as the description of the
   * digest URI SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_DIGEST_URI =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 180;



  /**
   * The message ID for the message that will be used as the description of the
   * authzID SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHZID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 181;


  /**
   * The message ID for the message that will be used as the description of the
   * SASL properties.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_SASL_PROPERTIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 182;



  /**
   * The message ID for the message that will be used as the description of the
   * KDC SASL property.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_KDC =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 183;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * specify multiple values for the KDC property.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPAUTH_KDC_SINGLE_VALUED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 184;



  /**
   * The message ID for the message that will be used if an invalid QoP mode is
   * requested.  This takes a single value, which is the requested QoP.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_INVALID_QOP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 185;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the JAAS temporary configuration for GSSAPI
   * authentication.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_JAAS_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 186;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform Kerberos authentication on the client system.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_LOCAL_AUTHENTICATION_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 187;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform GSSAPI authentication to the Directory Server.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_REMOTE_AUTHENTICATION_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 188;



  /**
   * The message ID for the message that will be used if the
   * LDAPAuthenticationHandler.run method is called for a non-SASL bind.  This
   * takes a single argument, which is a backtrace of the current thread showing
   * the invalid call.
   */
  public static final int MSGID_LDAPAUTH_NONSASL_RUN_INVOCATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 189;



  /**
   * The message ID for the message that will be used if the
   * LDAPAuthenticationHandler.run method is called for a SASL bind using an
   * unexpected mechanism.  This takes two arguments, which are the SASL
   * mechanism and a backtrace of the current thread showing the invalid call.
   */
  public static final int MSGID_LDAPAUTH_UNEXPECTED_RUN_INVOCATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 190;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a SASL client to handle the GSSAPI authentication.
   * This takes a single argument, which is a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_SASL_CLIENT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 191;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the initial challenge for GSSAPI authentication.  This
   * takes a single argument, which is a string representation of the exception
   * that was caught.
   */
  public static final int
       MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_INITIAL_CHALLENGE =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 192;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to validate the server SASL credentials included in a GSSAPI bind
   * response.  This takes a single argument, which is a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_CANNOT_VALIDATE_SERVER_CREDS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 193;



  /**
   * The message ID for the message that will be used if the Directory Server
   * indicates that a GSSAPI bind is complete when the SASL client does not
   * believe that to be the case.  This does not take any arguments.
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_UNEXPECTED_SUCCESS_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 194;



  /**
   * The message ID for the message that will be used if the Directory Server
   * sent a bind response that was neither "success" nor "SASL bind in
   * progress".  This takes three arguments, which are the result code, a
   * string representation of the result code, and the error message from the
   * bind response (if any).
   */
  public static final int MSGID_LDAPAUTH_GSSAPI_BIND_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 195;



  /**
   * The message ID for the message that will be used if the
   * LDAPAuthenticationHandler.handle method is called for a non-SASL bind.
   * This takes a single argument, which is a backtrace of the current thread
   * showing the invalid call.
   */
  public static final int MSGID_LDAPAUTH_NONSASL_CALLBACK_INVOCATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 196;



  /**
   * The message ID for the message that will be used if the
   * LDAPAuthenticationHandler.handle method is called for the GSSAPI mechanism
   * but with an unexpected callback type.  This takes a single argument, which
   * is a string representation of the callback type.
   */
  public static final int MSGID_LDAPAUTH_UNEXPECTED_GSSAPI_CALLBACK =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 197;



  /**
   * The message ID for the message that will be used if the
   * LDAPAuthenticationHandler.handle method is called for a SASL bind with an
   * unexpected mechanism.  This takes two arguments, which are the SASL
   * mechanism and a backtrace of the current thread showing the invalid call.
   */
  public static final int MSGID_LDAPAUTH_UNEXPECTED_CALLBACK_INVOCATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 198;



  /**
   * The message ID for the message that will be used to interactively prompt
   * a user for an authentication password.  This takes a single argument, which
   * is the username or bind DN for which to retrieve the password.
   */
  public static final int MSGID_LDAPAUTH_PASSWORD_PROMPT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 199;


  /**
   * The message ID for the message that will be used as the description of the
   * version argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_VERSION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 200;


  /**
   * The message ID for the message that will be used as the description of the
   * invalid version message.
   */
  public static final int MSGID_DESCRIPTION_INVALID_VERSION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 201;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to send a "Who Am I?" request to the Directory Server.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_SEND_WHOAMI_REQUEST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 202;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the "Who Am I?" request to the Directory Server.  This takes
   * a single argument, which is a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_LDAPAUTH_CANNOT_READ_WHOAMI_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 203;



  /**
   * The message ID for the message that will be used if the "Who Am I?" request
   * was rejected by the server.  This takes three arguments, which are the
   * result code from the response, a string representation of that result code,
   * and the error message from the response.
   */
  public static final int MSGID_LDAPAUTH_WHOAMI_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 204;


  /**
   * The message ID for the message that will be used if an invalid search
   * scope is provided.
   */
  public static final int MSGID_SEARCH_INVALID_SEARCH_SCOPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 205;


  /**
   * The message ID for the message that will be used if no filters
   * are specified for the search request.
   */
  public static final int MSGID_SEARCH_NO_FILTERS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 206;


  /**
   * The message ID for the message that will be used as the description of the
   * index name argument.  This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_DESCRIPTION_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 207;


  /**
   * The message ID for the message that will be used as the description of the
   * index name argument.  This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_DESCRIPTION_INDEX_NAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 208;


  /**
   * The message ID for the message that will be used as the description of the
   * argument requesting that an index should be verified to ensure it is clean.
   * This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_DESCRIPTION_VERIFY_CLEAN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 209;


  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform index verification.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_ERROR_DURING_VERIFY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 210;


  /**
   * The message ID for the message that will be used if a request to
   * verify an index for cleanliness does not specify a single index.
   */
  public static final int MSGID_VERIFYINDEX_VERIFY_CLEAN_REQUIRES_SINGLE_INDEX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 211;


  /**
   * The message ID for the message that will be used if a request to
   * verify indexes specifies the base DN of a backend that does not
   * support indexing.
   */
  public static final int MSGID_VERIFYINDEX_WRONG_BACKEND_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 212;



  /**
   * The message ID for the message that will be used if the backend selected
   * for an LDIF export does not support that operation.  This takes a single
   * argument, which is the requested backend ID.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_EXPORT_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 213;



  /**
   * The message ID for the message that will be used if the backend selected
   * for an LDIF import does not support that operation.  This takes a single
   * argument, which is the requested base DN.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_IMPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 214;



  /**
   * The message ID for the message that will be used as the description of the
   * dontWrap property.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_DONT_WRAP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 215;



  /**
   * The message ID for the message that will be used as the description of the
   * includeBranch argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_INCLUDE_BRANCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 216;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend ID.  This takes two arguments, which are
   * the DN of the backend configuration entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 217;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the include branch string.  This takes two arguments,
   * which are the provided base DN string and a message explaining the problem
   * that was encountered.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 218;



  /**
   * The message ID for the message that will be used if a requested include
   * base does not exist in the targeted backend.  This takes two arguments,
   * which are the specified include branch DN and the requested backend ID.
   */
  public static final int MSGID_LDIFIMPORT_INVALID_INCLUDE_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 219;



  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 220;



  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 221;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 222;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 223;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 224;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_VERIFYINDEX_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 225;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 226;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 227;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the core Directory Server configuration.  This takes a
   * single argument, which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_INITIALIZE_CORE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 228;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the base DN string.  This takes two arguments, which are
   * the provided base DN string and a message explaining the problem that
   * was encountered.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_DECODE_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 229;



  /**
   * The message ID for the message that will be used if multiple backends claim
   * to support the requested base DN.  This takes a single argument, which is
   * the requested base DN.
   */
  public static final int MSGID_VERIFYINDEX_MULTIPLE_BACKENDS_FOR_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 230;



  /**
   * The message ID for the message that will be used if no backends claim to
   * support the requested base DN.  This takes a single argument, which is the
   * requested base DN.
   */
  public static final int MSGID_VERIFYINDEX_NO_BACKENDS_FOR_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 231;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the backend configuration base DN string.  This takes two
   * arguments, which are the backend config base DN string and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_DECODE_BACKEND_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 232;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the backend configuration base entry.  This takes two
   * arguments, which are the DN of the entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 233;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend class name.  This takes two arguments,
   * which are the DN of the backend configuration entry and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_DETERMINE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 234;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for a Directory Server backend.  This takes three
   * arguments, which are the class name, the DN of the configuration entry, and
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_LOAD_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 235;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a class for a Directory Server backend.  This takes
   * three arguments, which are the class name, the DN of the configuration
   * entry, and a message explaining the problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_INSTANTIATE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 236;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not define any base DNs.  This takes a single argument, which is
   * the DN of the backend configuration entry.
   */
  public static final int MSGID_VERIFYINDEX_NO_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 237;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of base DNs for a backend.  This takes two
   * arguments, which are the DN of the backend configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_DETERMINE_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 238;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend ID.  This takes two arguments, which are
   * the DN of the backend configuration entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 239;



  /**
   * The message ID for the message that will be used as the description of the
   * includeBranch argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_BRANCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 240;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the include branch string.  This takes two arguments,
   * which are the provided base DN string and a message explaining the problem
   * that was encountered.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_DECODE_INCLUDE_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 241;



  /**
   * The message ID for the message that will be used a requested include base
   * does not exist in the targeted backend.  This takes two arguments, which
   * are the specified include branch DN and the requested backend ID.
   */
  public static final int MSGID_LDIFEXPORT_INVALID_INCLUDE_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 242;



  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 243;



  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 244;



  /**
   * The message ID for the message that will be used as the description of the
   * backendID argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 245;



  /**
   * The message ID for the message that will be used as the description of the
   * backupID argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_BACKUP_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 246;



  /**
   * The message ID for the message that will be used as the description of the
   * backupDirectory argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_BACKUP_DIR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 247;



  /**
   * The message ID for the message that will be used as the description of the
   * incremental argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_INCREMENTAL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 248;



  /**
   * The message ID for the message that will be used as the description of the
   * compress argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_COMPRESS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 249;



  /**
   * The message ID for the message that will be used as the description of the
   * encrypt argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_ENCRYPT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 250;



  /**
   * The message ID for the message that will be used as the description of the
   * hash argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_HASH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 251;



  /**
   * The message ID for the message that will be used as the description of the
   * signHash argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_SIGN_HASH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 252;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 253;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 254;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 255;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_BACKUPDB_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 256;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 257;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 258;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the core Directory Server configuration.  This takes a
   * single argument, which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_INITIALIZE_CORE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 259;



  /**
   * The message ID for the message that will be used if multiple backends claim
   * to have the requested backend ID.  This takes a single argument, which is
   * the requested backend ID.
   */
  public static final int MSGID_BACKUPDB_MULTIPLE_BACKENDS_FOR_ID=
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 260;



  /**
   * The message ID for the message that will be used if no backends claim to
   * have the requested backend ID.  This takes a single argument, which is the
   * requested backend ID.
   */
  public static final int MSGID_BACKUPDB_NO_BACKENDS_FOR_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 261;



  /**
   * The message ID for the message that will be used if the DN of the
   * configuration entry for the backend to archive does not match the DN of
   * the configuration entry used to generate previous backups in the same
   * backup directory.  This takes four arguments, which are the backend ID for
   * the backend to archive, the DN of the configuration entry for the backend
   * to archive, the path to the backup directory, and the DN of the
   * configuration entry for the backend used in previous backups into that
   * target directory.
   */
  public static final int MSGID_BACKUPDB_CONFIG_ENTRY_MISMATCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 262;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to use the requested path as a backup directory.  This takes two
   * arguments, which are the provided backup directory path, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_BACKUPDB_INVALID_BACKUP_DIR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 263;



  /**
   * The message ID for the message that will be used if the requested backend
   * cannot be backed up with the specified configuration.  This takes two
   * arguments, which are the backend ID for the target backend and a message
   * explaining the reason that the backup cannot be created.
   */
  public static final int MSGID_BACKUPDB_CANNOT_BACKUP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 264;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to perform a backup.  This takes two arguments, which are the
   * backend ID for the target backend and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_BACKUPDB_ERROR_DURING_BACKUP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 265;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the backend configuration base DN string.  This takes two
   * arguments, which are the backend config base DN string and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_DECODE_BACKEND_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 266;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the backend configuration base entry.  This takes two
   * arguments, which are the DN of the entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 267;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend class name.  This takes two arguments,
   * which are the DN of the backend configuration entry and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 268;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for a Directory Server backend.  This takes three
   * arguments, which are the class name, the DN of the configuration entry, and
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_LOAD_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 269;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a class for a Directory Server backend.  This takes
   * three arguments, which are the class name, the DN of the configuration
   * entry, and a message explaining the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_INSTANTIATE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 270;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not define any base DNs.  This takes a single argument, which is
   * the DN of the backend configuration entry.
   */
  public static final int MSGID_BACKUPDB_NO_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 271;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of base DNs for a backend.  This takes two
   * arguments, which are the DN of the backend configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_BACKUPDB_CANNOT_DETERMINE_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 272;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend ID.  This takes two arguments, which are
   * the DN of the backend configuration entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 273;



  /**
   * The message ID for the message that will be used as the description of the
   * backUpAll argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_BACKUP_ALL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 274;



  /**
   * The message ID for the message that will be used if both the backUpAll and
   * backendID arguments were used.  This takes two arguments, which are the
   * long identifiers for the backUpAll and backendID arguments.
   */
  public static final int MSGID_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 275;



  /**
   * The message ID for the message that will be used if neither the backUpAll
   * nor the backendID arguments was used.  This takes two arguments, which are
   * the long identifiers for the backUpAll and backendID arguments.
   */
  public static final int MSGID_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 276;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the backup directory structure.  This takes two arguments,
   * which are the path to the backup directory and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_BACKUPDB_CANNOT_CREATE_BACKUP_DIR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 277;



  /**
   * The message ID for the message that will be used if a request is made to
   * back up a backend that does not provide such a mechanism.  This takes a
   * single argument, which is the backend ID of the target backend.
   */
  public static final int MSGID_BACKUPDB_BACKUP_NOT_SUPPORTED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 278;



  /**
   * The message ID for the message that will be used if none of the requested
   * backends support a backup mechanism.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_NO_BACKENDS_TO_ARCHIVE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 279;



  /**
   * The message ID for the message that will be used when starting the backup
   * process for a backend.  This takes a single argument, which is the backend
   * ID for that backend.
   */
  public static final int MSGID_BACKUPDB_STARTING_BACKUP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_NOTICE | 280;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a backup descriptor file.  This takes two arguments, which
   * are the path to the descriptor file and a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 281;



  /**
   * The message ID for the message that will be used when the backup process
   * is complete but one or more errors were encountered during processing.
   * This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_COMPLETED_WITH_ERRORS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_NOTICE | 282;



  /**
   * The message ID for the message that will be used when the backup process
   * completes without any errors.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_COMPLETED_SUCCESSFULLY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_NOTICE | 283;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the crypto manager.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 284;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the crypto manager.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 285;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the crypto manager.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_INITIALIZE_CRYPTO_MANAGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 286;



  /**
   * The message ID for the message that will be used as the description of the
   * incrementalBaseID argument.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_DESCRIPTION_INCREMENTAL_BASE_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 287;



  /**
   * The message ID for the message that will be used if an incremental base ID
   * is specified for a full backup.  This takes two arguments, which are the
   * long identifiers for the incremental base ID and the incremental arguments.
   */
  public static final int MSGID_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 288;



  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 289;



  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 290;



  /**
   * The message ID for the message that will be used as the description of the
   * backendID argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 291;



  /**
   * The message ID for the message that will be used as the description of the
   * backupID argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_BACKUP_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 292;



  /**
   * The message ID for the message that will be used as the description of the
   * backupDirectory argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_BACKUP_DIR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 293;



  /**
   * The message ID for the message that will be used as the description of the
   * listBackups argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_LIST_BACKUPS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 294;



  /**
   * The message ID for the message that will be used as the description of the
   * verifyOnly argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_VERIFY_ONLY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 295;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 296;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 297;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 298;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_RESTOREDB_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 299;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 300;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 301;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the core Directory Server configuration.  This takes a
   * single argument, which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_INITIALIZE_CORE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 302;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the crypto manager.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_INITIALIZE_CRYPTO_MANAGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 303;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the backup descriptor contained in the specified backup
   * directory.  This takes two arguments, which are the path to the backup
   * directory and a string representation of the exception that was caught.
   */
  public static final int MSGID_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 304;



  /**
   * The message ID for the message that will be to display the backup ID when
   * obtaining a list of available backups in a given directory.  This takes a
   * single argument, which is the backup ID.
   */
  public static final int MSGID_RESTOREDB_LIST_BACKUP_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 305;



  /**
   * The message ID for the message that will be to display the backup date when
   * obtaining a list of available backups in a given directory.  This takes a
   * single argument, which is a string representation of the backup date.
   */
  public static final int MSGID_RESTOREDB_LIST_BACKUP_DATE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 306;



  /**
   * The message ID for the message that will be to indicate whether a backup is
   * incremental when obtaining a list of available backups in a given
   * directory.  This takes a single argument, which is a string representation
   * of whether the backup is incremental.
   */
  public static final int MSGID_RESTOREDB_LIST_INCREMENTAL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 307;



  /**
   * The message ID for the message that will be to indicate whether a backup is
   * compressed when obtaining a list of available backups in a given
   * directory.  This takes a single argument, which is a string representation
   * of whether the backup is compressed.
   */
  public static final int MSGID_RESTOREDB_LIST_COMPRESSED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 308;



  /**
   * The message ID for the message that will be to indicate whether a backup is
   * encrypted when obtaining a list of available backups in a given
   * directory.  This takes a single argument, which is a string representation
   * of whether the backup is encrypted.
   */
  public static final int MSGID_RESTOREDB_LIST_ENCRYPTED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 309;



  /**
   * The message ID for the message that will be to indicate whether a backup is
   * hashed when obtaining a list of available backups in a given directory.
   * This takes a single argument, which is a string representation of whether
   * the backup is hashed.
   */
  public static final int MSGID_RESTOREDB_LIST_HASHED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 310;



  /**
   * The message ID for the message that will be to indicate whether a backup is
   * signed when obtaining a list of available backups in a given directory.
   * This takes a single argument, which is a string representation of whether
   * the backup is signed.
   */
  public static final int MSGID_RESTOREDB_LIST_SIGNED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 311;



  /**
   * The message ID for the message that will be to display the set of
   * dependencies when obtaining a list of available backups in a given
   * directory.  This takes a single argument, which is a comma-separated list
   * of the  dependencies for the backup.
   */
  public static final int MSGID_RESTOREDB_LIST_DEPENDENCIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 312;



  /**
   * The message ID for the message that will be used if the user requested a
   * backup ID that does not exist.  This takes two arguments, which are the
   * provided backup ID and the path to the backup directory.
   */
  public static final int MSGID_RESTOREDB_INVALID_BACKUP_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 313;



  /**
   * The message ID for the message that will be used if the specified backup
   * directory does not contain any backups.  This takes a single argument,
   * which is the path tot he backup directory.
   */
  public static final int MSGID_RESTOREDB_NO_BACKUPS_IN_DIRECTORY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 314;



  /**
   * The message ID for the message that will be used if the backup directory
   * is associated with a backend that does not exist.  This takes two
   * arguments, which are the path to the backup directory and the DN of the
   * configuration entry for the backups contained in that directory.
   */
  public static final int MSGID_RESTOREDB_NO_BACKENDS_FOR_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 315;



  /**
   * The message ID for the message that will be used if the selected backend
   * does not support the ability to perform restore operations.  This takes a
   * single argument, which is the backend ID for the selected backend.
   */
  public static final int MSGID_RESTOREDB_CANNOT_RESTORE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 316;



  /**
   * The message ID for the message that will be used if an error occurred while
   * attempting to restore the backup.  This takes three arguments, which are
   * the backup ID, the path to the backup directory, and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_ERROR_DURING_BACKUP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 317;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the backend configuration base DN string.  This takes two
   * arguments, which are the backend config base DN string and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_DECODE_BACKEND_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 318;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the backend configuration base entry.  This takes two
   * arguments, which are the DN of the entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 319;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend class name.  This takes two arguments,
   * which are the DN of the backend configuration entry and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 320;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for a Directory Server backend.  This takes three
   * arguments, which are the class name, the DN of the configuration entry, and
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_LOAD_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 321;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a class for a Directory Server backend.  This takes
   * three arguments, which are the class name, the DN of the configuration
   * entry, and a message explaining the problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_INSTANTIATE_BACKEND_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 322;



  /**
   * The message ID for the message that will be used if a backend configuration
   * entry does not define any base DNs.  This takes a single argument, which is
   * the DN of the backend configuration entry.
   */
  public static final int MSGID_RESTOREDB_NO_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 323;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of base DNs for a backend.  This takes two
   * arguments, which are the DN of the backend configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_RESTOREDB_CANNOT_DETERMINE_BASES_FOR_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 324;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend ID.  This takes two arguments, which are
   * the DN of the backend configuration entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 325;



  /**
   * The message ID for the message that will be used if the signHash option was
   * used without the hash option.  This takes two arguments, which are the
   * long identifiers for the signHash and the hash arguments.
   */
  public static final int MSGID_BACKUPDB_SIGN_REQUIRES_HASH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 326;


  /**
   * The message ID for the message that will be used as the description of the
   * no-op argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_NOOP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 327;


  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to acquire a lock for the backend to be archived.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_LOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 328;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a lock for the backend to be archived.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 329;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to acquire a lock for the backend to be restored.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_LOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 330;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a lock for the backend to be restored.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_RESTOREDB_CANNOT_UNLOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 331;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to acquire a lock for the backend to be imported.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_LOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 332;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a lock for the backend to be imported.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_UNLOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 333;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to acquire a lock for the backend to be exported.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_LOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 334;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a lock for the backend to be exported.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_UNLOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 335;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to acquire a lock for the backend to be verified.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_LOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 336;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to release a lock for the backend to be verified.  This takes
   * two arguments, which are the backend ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_VERIFYINDEX_CANNOT_UNLOCK_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 337;

  /**
   * The message ID for the message that will be used as the description of the
   * types only argument for the search results.  This does not take any
   * arguments.
   */
  public static final int MSGID_DESCRIPTION_TYPES_ONLY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 338;



  /**
   * The message ID for the message that will be used as the description of the
   * skipSchemaValidation argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_SKIP_SCHEMA_VALIDATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 339;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the server plugins.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 340;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the server plugins.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 341;



  /**
   * The message ID for the message that will be used as the description of the
   * assertionFilter option.  It does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_ASSERTION_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 342;



  /**
   * The message ID for the message that will be used if a request is made to
   * use the LDAP assertion control but the provided filter is invalid.  This
   * takes a single argument, which is a message explaining why the filter is
   * invalid.
   */
  public static final int MSGID_LDAP_ASSERTION_INVALID_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 343;



  /**
   * The message ID for the message that will be used as the description of the
   * preReadAttributes option.  It does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_PREREAD_ATTRS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 346;



  /**
   * The message ID for the message that will be used as the description of the
   * postReadAttributes option.  It does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_POSTREAD_ATTRS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 347;



  /**
   * The message ID for the message that will be used if the pre-read response
   * control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_LDAPMODIFY_PREREAD_NO_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 348;



  /**
   * The message ID for the message that will be used if the pre-read response
   * control value cannot be decoded.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 349;



  /**
   * The message ID for the message that will be used as the heading for the
   * entry displayed from the pre-read response control.  It does not take any
   * arguments.
   */
  public static final int MSGID_LDAPMODIFY_PREREAD_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 350;



  /**
   * The message ID for the message that will be used if the post-read response
   * control does not have a value.  This does not take any arguments.
   */
  public static final int MSGID_LDAPMODIFY_POSTREAD_NO_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 351;



  /**
   * The message ID for the message that will be used if the post-read response
   * control value cannot be decoded.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 352;



  /**
   * The message ID for the message that will be used as the heading for the
   * entry displayed from the post-read response control.  It does not take any
   * arguments.
   */
  public static final int MSGID_LDAPMODIFY_POSTREAD_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 353;



  /**
   * The message ID for the message that will be used as the description for the
   * command-line option that includes the proxied authorization control in the
   * request.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_PROXY_AUTHZID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 354;



  /**
   * The message ID for the message that will be used as the description for the
   * command-line option that includes the persistent search control in the
   * request.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_PSEARCH_INFO =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 355;



  /**
   * The message ID for the message that will be used if a request is made to
   * use the persistent search control but the descriptor string is empty.  This
   * does not take any arguments.
   */
  public static final int MSGID_PSEARCH_MISSING_DESCRIPTOR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 356;



  /**
   * The message ID for the message that will be used if a request is made to
   * use the persistent search control but the descriptor string does not start
   * with "ps".  This takes a single argument, which is the provided descriptor
   * string.
   */
  public static final int MSGID_PSEARCH_DOESNT_START_WITH_PS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 357;



  /**
   * The message ID for the message that will be used if the persistent search
   * control descriptor contains an invalid change type.  This takes a single
   * argument, which is the invalid change type.
   */
  public static final int MSGID_PSEARCH_INVALID_CHANGE_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 358;



  /**
   * The message ID for the message that will be used if the persistent search
   * control descriptor contains an invalid changesOnly value.  This takes a
   * single argument, which is the invalid changesOnly value.
   */
  public static final int MSGID_PSEARCH_INVALID_CHANGESONLY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 359;



  /**
   * The message ID for the message that will be used if the persistent search
   * control descriptor contains an invalid returnECs value.  This takes a
   * single argument, which is the invalid returnECs value.
   */
  public static final int MSGID_PSEARCH_INVALID_RETURN_ECS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 360;



  /**
   * The message ID for the message that will be used as the description for the
   * command-line option that requests that the authzID be included in the bind
   * response.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_REPORT_AUTHZID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 361;



  /**
   * The message ID for the message that will be used to report the
   * authorization ID included in the bind response to the user.  This takes a
   * single argument, which is the authorization ID.
   */
  public static final int MSGID_BIND_AUTHZID_RETURNED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 362;

  /**
   * The message ID for the message that will be used as the description of the
   * filename argument.  This does not take any arguments.
   */
  public static final int MSGID_SEARCH_DESCRIPTION_FILENAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 363;



  /**
   * The message ID for the message that will be used as the description of the
   * matchedValuesFilter option for the ldapsearch tool.  It does not take any
   * arguments.
   */
  public static final int MSGID_DESCRIPTION_MATCHED_VALUES_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 364;



  /**
   * The message ID for the message that will be used if a request is made to
   * use the matched values but the provided filter is invalid.  This takes a
   * single argument, which is a message explaining why the filter is invalid.
   */
  public static final int MSGID_LDAP_MATCHEDVALUES_INVALID_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 365;



  /**
   * The message ID for the message that will be used if the modify
   * tool cannot open the LDIF file for reading.  This takes two
   * arguments, which are the path to the LDIF file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_FILE_CANNOT_OPEN_FOR_READ =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_FATAL_ERROR | 366;



  /**
   * The message ID for the message that will be used if an I/O error occurs
   * while attempting to read from the LDIF file.  This takes two
   * arguments, which are the path to the LDIF file and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LDIF_FILE_READ_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_FATAL_ERROR | 367;



  /**
   * The message ID for the message that will be used if an entry in the
   * LDIF file cannot be parsed as a valid LDIF entry.  This takes
   * three arguments, which are the approximate line number in the LDIF file,
   * the path to the LDIF file, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_LDIF_FILE_INVALID_LDIF_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 368;



  /**
   * The message ID for the message that will be used as the description of the
   * authPasswordSyntax argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_AUTHPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 369;



  /**
   * The message ID for the message that will be used if no auth password
   * storage schemes have been defined in the Directory Server.  This does not
   * take any arguments.
   */
  public static final int MSGID_ENCPW_NO_AUTH_STORAGE_SCHEMES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 370;



  /**
   * The message ID for the message that will be used if the requested auth
   * password storage scheme is not configured for use in the Directory Server.
   * This takes a single argument, which is the name of the requested storage
   * scheme.
   */
  public static final int MSGID_ENCPW_NO_SUCH_AUTH_SCHEME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 371;



  /**
   * The message ID for the message that will be used if the encoded password is
   * not valid according to the auth password syntax.  This takes a single
   * argument, which is a message explaining why it is invalid.
   */
  public static final int MSGID_ENCPW_INVALID_ENCODED_AUTHPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 372;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the password policy components.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_INITIALIZE_PWPOLICY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 373;



  /**
   * The message ID for the message that will be used as the description of the
   * host argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_HOST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 374;



  /**
   * The message ID for the message that will be used as the description of the
   * port argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 375;



  /**
   * The message ID for the message that will be used as the description of the
   * useSSL argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_USESSL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 376;



  /**
   * The message ID for the message that will be used as the description of the
   * useStartTLS argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_USESTARTTLS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 377;



  /**
   * The message ID for the message that will be used as the description of the
   * bindDN argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_BINDDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 378;



  /**
   * The message ID for the message that will be used as the description of the
   * bindPassword argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_BINDPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 379;



  /**
   * The message ID for the message that will be used as the description of the
   * bindPasswordFile argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_BINDPWFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 380;



  /**
   * The message ID for the message that will be used as the description of the
   * saslOption argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_SASLOPTIONS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 381;



  /**
   * The message ID for the message that will be used as the description of the
   * proxyAuthZID argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_PROXYAUTHZID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 382;



  /**
   * The message ID for the message that will be used as the description of the
   * stopReason argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_STOP_REASON =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 383;



  /**
   * The message ID for the message that will be used as the description of the
   * stopTime argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_STOP_TIME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 384;



  /**
   * The message ID for the message that will be used as the description of the
   * trustAll argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_TRUST_ALL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 385;



  /**
   * The message ID for the message that will be used as the description of the
   * keyStoreFile argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_KSFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 386;



  /**
   * The message ID for the message that will be used as the description of the
   * keyStorePassword argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_KSPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 387;



  /**
   * The message ID for the message that will be used as the description of the
   * keyStorePasswordFile argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_KSPWFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 388;



  /**
   * The message ID for the message that will be used as the description of the
   * trustStoreFile argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_TSFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 389;



  /**
   * The message ID for the message that will be used as the description of the
   * trustStorePassword argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_TSPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 390;



  /**
   * The message ID for the message that will be used as the description of the
   * trustStorePasswordFile argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_TSPWFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 391;



  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_SHOWUSAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 392;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 393;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 394;



  /**
   * The message ID for the message that will be used if two arguments that are
   * mutually exclusive were both provided.  This takes two arguments, which are
   * the long identifiers for the mutually-exclusive command line arguments.
   */
  public static final int MSGID_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 395;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the provided stop time.  This does not take any arguments.
   */
  public static final int MSGID_STOPDS_CANNOT_DECODE_STOP_TIME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 396;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to perform SSL initialization.  This takes a single argument, which
   * is a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_CANNOT_INITIALIZE_SSL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 397;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a SASL option string.  This takes a single argument, which
   * is the SASL option string.
   */
  public static final int MSGID_STOPDS_CANNOT_PARSE_SASL_OPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 398;



  /**
   * The message ID for the message that will be used if SASL options were used
   * without specifying the SASL mechanism.  This does not take any arguments.
   */
  public static final int MSGID_STOPDS_NO_SASL_MECHANISM =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 399;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the port to use to communicate with the Directory
   * Server.  This takes two arguments, which are the name of the port argument
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_CANNOT_DETERMINE_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 400;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to connect to the Directory Server.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_CANNOT_CONNECT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 401;



  /**
   * The message ID for the message that will be used if the connection is
   * closed while waiting for the response from the Directory Server.  This does
   * not take any arguments.
   */
  public static final int MSGID_STOPDS_UNEXPECTED_CONNECTION_CLOSURE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 402;



  /**
   * The message ID for the message that will be used if an I/O error occurs
   * while communicating with the Directory Server.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_IO_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 403;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the response from the Directory Server.  This takes a
   * single  argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_STOPDS_DECODE_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 404;



  /**
   * The message ID for the message that will be used if an unexpected response
   * type was received for the add request.  This takes a single argument, which
   * is the name of the response type that was received.
   */
  public static final int MSGID_STOPDS_INVALID_RESPONSE_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 405;



  /**
   * The message ID for the message that will be used to indicate that the
   * user's password is expired.  This does not take any arguments.
   */
  public static final int MSGID_BIND_PASSWORD_EXPIRED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 406;



  /**
   * The message ID for the message that will be used to indicate that the
   * user's password will expire in the near future.  This takes a single
   * argument, which indicates the length of time until the password is actually
   * expired.
   */
  public static final int MSGID_BIND_PASSWORD_EXPIRING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 407;



  /**
   * The message ID for the message that will be used to indicate that the
   * user's account has been locked.  This does not take any arguments.
   */
  public static final int MSGID_BIND_ACCOUNT_LOCKED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 408;



  /**
   * The message ID for the message that will be used to indicate that the
   * user's password must be changed before any other operations will be
   * allowed.  This does not take any arguments.
   */
  public static final int MSGID_BIND_MUST_CHANGE_PASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 409;



  /**
   * The message ID for the message that will be used to specify the number of
   * grace logins that the user has left.  This takes a single argument, which
   * is the number of grace logins remaining.
   */
  public static final int MSGID_BIND_GRACE_LOGINS_REMAINING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 410;



  /**
   * The message ID for the message that will be used as the description for the
   * command-line option that requests that the password policy control be used
   * in the bind operation.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_USE_PWP_CONTROL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 411;



  /**
   * The message ID for the message that will be used as the description of the
   * restart argument.  It does not take any arguments.
   */
  public static final int MSGID_STOPDS_DESCRIPTION_RESTART =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 412;


  /**
   * The message ID for the message that will be used as the description of the
   * filename argument.  This does not take any arguments.
   */
  public static final int MSGID_COMPARE_DESCRIPTION_FILENAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 413;


  /**
   * The message ID for the message that will be used as the description of the
   * ldifFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_LDIF_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 414;


  /**
   * The message ID for the message that will be used as the description of the
   * baseDN argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_BASEDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 415;


  /**
   * The message ID for the message that will be used as the description of the
   * scope argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_SCOPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 416;


  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 417;


  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 418;


  /**
   * The message ID for the message that will be used as the description of the
   * filterFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_FILTER_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 419;


  /**
   * The message ID for the message that will be used as the description of the
   * outputFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_OUTPUT_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 420;


  /**
   * The message ID for the message that will be used as the description of the
   * overwriteExisting argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_OVERWRITE_EXISTING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 421;


  /**
   * The message ID for the message that will be used as the description of the
   * dontWrap argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_DONT_WRAP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 422;


  /**
   * The message ID for the message that will be used as the description of the
   * sizeLimit argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_SIZE_LIMIT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 423;


  /**
   * The message ID for the message that will be used as the description of the
   * timeLimit argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_TIME_LIMIT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 424;


  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 425;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 426;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 427;



  /**
   * The message ID for the message that will be used if no filter file or
   * single filter was provided.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_NO_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 428;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server configuration.  This takes two
   * arguments, which are the path to the Directory Server configuration file
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_INITIALIZE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 429;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server schema.  This takes two arguments, which
   * are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_INITIALIZE_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 430;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a search filter.  This takes two arguments, which are the
   * provided filter string and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_PARSE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 431;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a base DN.  This takes two arguments, which are the
   * provided base DN string and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_PARSE_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 432;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the time limit.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_PARSE_TIME_LIMIT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 433;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the size limit.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_PARSE_SIZE_LIMIT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 434;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the LDIF reader.  This takes a single argument, which is
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_CREATE_READER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 435;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the LDIF writer.  This takes a single argument, which is
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_CREATE_WRITER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 436;



  /**
   * The message ID for the message that will be used if the configured time
   * limit has been exceeded.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_TIME_LIMIT_EXCEEDED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 437;



  /**
   * The message ID for the message that will be used if the configured size
   * limit has been exceeded.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_SIZE_LIMIT_EXCEEDED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 438;



  /**
   * The message ID for the message that will be used if a recoverable error
   * occurs while attempting to read an entry.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_READ_ENTRY_RECOVERABLE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 439;



  /**
   * The message ID for the message that will be used if a fatal error occurs
   * while attempting to read an entry.  This takes a single argument, which is
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_READ_ENTRY_FATAL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 440;



  /**
   * The message ID for the message that will be used if an error occurs during
   * search processing.  This takes a single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_ERROR_DURING_PROCESSING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 441;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server JMX subsystem.  This takes two arguments,
   * which are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFSEARCH_CANNOT_INITIALIZE_JMX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 442;


  /**
   * The message ID for the message that will be used as the description of the
   * sourceLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_SOURCE_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 443;


  /**
   * The message ID for the message that will be used as the description of the
   * targetLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_TARGET_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 444;


  /**
   * The message ID for the message that will be used as the description of the
   * outputLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_OUTPUT_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 445;


  /**
   * The message ID for the message that will be used as the description of the
   * overwriteExisting argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_OVERWRITE_EXISTING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 446;


  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 447;


  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 448;


  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 449;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 450;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 451;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server JMX subsystem.  This takes two arguments,
   * which are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_INITIALIZE_JMX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 452;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server configuration.  This takes two
   * arguments, which are the path to the Directory Server configuration file
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_INITIALIZE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 453;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server schema.  This takes two arguments, which
   * are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_INITIALIZE_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 454;



  /**
   * The message ID for the message that will be used if an error occurs while
   * opening the source LDIF file.  This takes two arguments, which are the name
   * of the LDIF file and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_OPEN_SOURCE_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 455;



  /**
   * The message ID for the message that will be used if an error occurs while
   * reading the source LDIF file.  This takes two arguments, which are the name
   * of the LDIF file and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_ERROR_READING_SOURCE_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 456;



  /**
   * The message ID for the message that will be used if an error occurs while
   * opening the target LDIF file.  This takes two arguments, which are the name
   * of the LDIF file and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_OPEN_TARGET_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 457;



  /**
   * The message ID for the message that will be used if an error occurs while
   * reading the target LDIF file.  This takes two arguments, which are the name
   * of the LDIF file and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_ERROR_READING_TARGET_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 458;



  /**
   * The message ID for the message that will be used if an error occurs while
   * opening the LDIF writer.  This takes a single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_CANNOT_OPEN_OUTPUT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 459;



  /**
   * The message ID for the message that will be used if no differences are
   * detected between the source and target LDIF files.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDIFDIFF_NO_DIFFERENCES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 460;



  /**
   * The message ID for the message that will be used if an error occurs while
   * writing diff information.  This takes single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFDIFF_ERROR_WRITING_OUTPUT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 461;


  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 462;


  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 463;


  /**
   * The message ID for the message that will be used as the description of the
   * ldapPort argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_LDAP_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 464;


  /**
   * The message ID for the message that will be used as the description of the
   * baseDN argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 465;


  /**
   * The message ID for the message that will be used as the description of the
   * rootDN argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_ROOT_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 466;


  /**
   * The message ID for the message that will be used as the description of the
   * rootPassword argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_ROOT_PW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 467;


  /**
   * The message ID for the message that will be used as the description of the
   * rootPasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_ROOT_PW_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 468;


  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 469;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 470;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 471;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to acquire an exclusive lock for the Directory Server process.  This
   * takes two argments, which are the path to the lock file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_ACQUIRE_SERVER_LOCK =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 472;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server JMX subsystem.  This takes two arguments,
   * which are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_INITIALIZE_JMX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 473;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server configuration.  This takes two
   * arguments, which are the path to the Directory Server configuration file
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_INITIALIZE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 474;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server schema.  This takes two arguments, which
   * are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_INITIALIZE_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 475;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a base DN.  This takes two arguments, which are the base DN
   * string and a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_PARSE_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 476;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the root DN.  This takes two arguments, which are the root
   * DN string and a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_PARSE_ROOT_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 477;



  /**
   * The message ID for the message that will be used if a root DN is provided
   * without giving a root password.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_NO_ROOT_PW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 478;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the base DNs.  This takes a single argument, which is
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_UPDATE_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 479;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the LDAP port.  This takes a single argument, which is
   * a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_UPDATE_LDAP_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 480;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the root user entry.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_UPDATE_ROOT_USER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 481;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to write the updated configuration.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_CONFIGDS_CANNOT_WRITE_UPDATED_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 482;



  /**
   * The message ID for the message that will be used if no configuration
   * changes were requested.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_NO_CONFIG_CHANGES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 483;



  /**
   * The message ID for the message that will be used to indicate that the
   * updated configuration has been written.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_WROTE_UPDATED_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 484;



  /**
   * The message ID for the message that will be used as the description for the
   * testOnly argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_TESTONLY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 485;



  /**
   * The message ID for the message that will be used as the description for the
   * programName argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_PROGNAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 486;



  /**
   * The message ID for the message that will be used as the description for the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 487;



  /**
   * The message ID for the message that will be used as the description for the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 488;



  /**
   * The message ID for the message that will be used as the description for the
   * silentInstall argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_SILENT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 489;



  /**
   * The message ID for the message that will be used as the description for the
   * baseDN argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_BASEDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 490;



  /**
   * The message ID for the message that will be used as the description for the
   * addBaseEntry argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_ADDBASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 491;



  /**
   * The message ID for the message that will be used as the description for the
   * importLDIF argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_IMPORTLDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 492;



  /**
   * The message ID for the message that will be used as the description for the
   * ldapPort argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_LDAPPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 493;



  /**
   * The message ID for the message that will be used as the description for the
   * skipPortCheck argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_SKIPPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 494;



  /**
   * The message ID for the message that will be used as the description for the
   * rootDN argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_ROOTDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 495;



  /**
   * The message ID for the message that will be used as the description for the
   * rootPassword argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_ROOTPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 496;



  /**
   * The message ID for the message that will be used as the description for the
   * rootPasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_ROOTPWFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 497;



  /**
   * The message ID for the message that will be used as the description for the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_HELP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 498;



  /**
   * The message ID for the message that will be used if the user did not
   * specify the path to the Directory Server configuration file.  This takes a
   * single argument, which is the name of the command-line option that should
   * be used to provide that information.
   */
  public static final int MSGID_INSTALLDS_NO_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 499;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server JMX subsystem.  This takes two arguments,
   * which are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_INITIALIZE_JMX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 500;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server configuration.  This takes two
   * arguments, which are the path to the Directory Server configuration file
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_INITIALIZE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 501;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server schema.  This takes two arguments, which
   * are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_INITIALIZE_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 502;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse a string as a DN.  This takes two arguments, which are the
   * DN string and a message explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_PARSE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 503;



  /**
   * The message ID for the message that will be used as the prompt to provide
   * the directory base DN.  It does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_BASEDN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 504;



  /**
   * The message ID for the message that will be used as the prompt to determine
   * whether to import data from LDIF.  It does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_IMPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 505;



  /**
   * The message ID for the message that will be used as the prompt to provide
   * the path to the LDIF file to import.  It does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_IMPORT_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 506;



  /**
   * The message ID for the message that will be used if two conflicting
   * arguments were provided to the program.  This takes two arguments, which
   * are the long forms of the conflicting arguments.
   */
  public static final int MSGID_INSTALLDS_TWO_CONFLICTING_ARGUMENTS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 507;



  /**
   * The message ID for the message that will be used as the prompt to determine
   * whether to add the base entry.  It does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_ADDBASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 508;



  /**
   * The message ID for the message that will be used as the prompt to determine
   * the LDAP port number to use.  It does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_LDAPPORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 509;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to bind to a privileged port.  This takes two arguments, which
   * are the port number and a message explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 510;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to bind to a non-privileged port.  This takes two arguments,
   * which are the port number and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_BIND_TO_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 511;



  /**
   * The message ID for the message that will be used as the prompt to determine
   * the initial root DN.  It does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_ROOT_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 512;



  /**
   * The message ID for the message that will be used if no root password was
   * provided when performing a silent installation.  This takes two arguments,
   * which are the long forms of the root password and root password file
   * arguments.
   */
  public static final int MSGID_INSTALLDS_NO_ROOT_PASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 513;



  /**
   * The message ID for the message that will be used as the prompt to request
   * the initial root password for the first time.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_ROOT_PASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 514;



  /**
   * The message ID for the message that will be used as the prompt to confirm
   * the initial root password.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_CONFIRM_ROOT_PASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 515;



  /**
   * The message ID for the message that will be used to indicate that the
   * server configuration is being updated.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_STATUS_CONFIGURING_DS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 516;



  /**
   * The message ID for the message that will be used to indicate that the
   * base LDIF file is being created.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_STATUS_CREATING_BASE_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 517;



  /**
   * The message ID for the message that will be used if an error occurs while
   * creating the base LDIF file.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_CREATE_BASE_ENTRY_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 518;



  /**
   * The message ID for the message that will be used to indicate that the
   * LDIF data is being imported.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_STATUS_IMPORTING_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 519;



  /**
   * The message ID for the message that will be used to indicate that the setup
   * process was successful.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_STATUS_SUCCESS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 520;



  /**
   * The message ID for the message that will be used as the prompt value for
   * Boolean "true" or "yes" values.
   */
  public static final int MSGID_INSTALLDS_PROMPT_VALUE_YES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 521;



  /**
   * The message ID for the message that will be used as the prompt value for
   * Boolean "false" or "no" values.
   */
  public static final int MSGID_INSTALLDS_PROMPT_VALUE_NO =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 522;



  /**
   * The message ID for the message that will be used to indicate that the
   * Boolean value could not be interpreted.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_INVALID_YESNO_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 523;



  /**
   * The message ID for the message that will be used to indicate that the
   * response value could not be interpreted as an integer.  This does not take
   * any arguments.
   */
  public static final int MSGID_INSTALLDS_INVALID_INTEGER_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 524;



  /**
   * The message ID for the message that will be used to indicate that the
   * provided integer value was below the lower bound.  This takes a single
   * argument, which is the lower bound.
   */
  public static final int MSGID_INSTALLDS_INTEGER_BELOW_LOWER_BOUND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 525;



  /**
   * The message ID for the message that will be used to indicate that the
   * provided integer value was above the upper bound.  This takes a single
   * argument, which is the upper bound.
   */
  public static final int MSGID_INSTALLDS_INTEGER_ABOVE_UPPER_BOUND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 526;



  /**
   * The message ID for the message that will be used to indicate that the
   * response value could not be interpreted as a DN.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_INVALID_DN_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 527;



  /**
   * The message ID for the message that will be used to indicate that the
   * response value was an invalid zero-length string.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_INVALID_STRING_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 528;



  /**
   * The message ID for the message that will be used to indicate that the
   * response value was an invalid zero-length string.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_INVALID_PASSWORD_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 529;



  /**
   * The message ID for the message that will be used to indicate that the
   * provided password values do not match.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PASSWORDS_DONT_MATCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 530;



  /**
   * The message ID for the message that will be used if an error occurs while
   * reading from standard input.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_ERROR_READING_FROM_STDIN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 531;



  /**
   * The message ID for the message that will be used as the description of the
   * quiet argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_QUIET =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 532;



  /**
   * The message ID for the message that will be used to indicate that the
   * LDIF import was successful.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_IMPORT_SUCCESSFUL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 533;



  /**
   * The message ID for the message that will be used if the user did not
   * specify the path to the Directory Server configuration file.  This takes a
   * single argument, which is the name of the command-line option that should
   * be used to provide that information.
   */
  public static final int MSGID_INSTALLDS_INITIALIZING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 534;



  /**
   * The message ID for the message that will be used if an invalid number of
   * arguments were provided for a MakeLDIF tag.  This takes four arguments,
   * which are the name of the tag, the line number on which it appears, the
   * expected number of arguments, and the actual number of arguments.
   */
  public static final int MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 535;



  /**
   * The message ID for the message that will be used if an invalid number of
   * arguments were provided for a MakeLDIF tag, when a range of arguments are
   * allowed.  This takes five arguments, which are the name of the tag, the
   * line number on which it appears, the minimum number of expected arguments,
   * the maximum number of expected arguments, and the actual number of
   * arguments.
   */
  public static final int MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 536;



  /**
   * The message ID for the message that will be used if an undefined attribute
   * type is referenced in a template file.  This takes two arguments, which are
   * the name of the attribute type and the line number on which it appears.
   */
  public static final int MSGID_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 537;



  /**
   * The message ID for the message that will be used if an integer value is
   * below the allowed lower bound.  This takes four arguments, which are the
   * provided integer value, the lower bound, the tag name, and the line number
   * on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 538;



  /**
   * The message ID for the message that will be used if value cannot be parsed
   * as an integer.  This takes three arguments, which are the provided value,
   * the tag name, and the line number on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 539;



  /**
   * The message ID for the message that will be used if an integer value is
   * above the allowed upper bound.  This takes four arguments, which are the
   * provided integer value, the upper bound, the tag name, and the line number
   * on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_INTEGER_ABOVE_UPPER_BOUND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 540;



  /**
   * The message ID for the message that will be used if a tag argument is an
   * empty string, which is not allowed.  This takes three arguments, which is
   * the position of the argument, the tag name, and the line number on which
   * it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_INVALID_EMPTY_STRING_ARGUMENT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 541;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a Boolean.  This takes three arguments, which are the provided
   * value, the tag name, and the line number on which it appears in the
   * template file.
   */
  public static final int MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_BOOLEAN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 542;



  /**
   * The message ID for the message that will be used if a branch references an
   * undefined subordinate template.  This takes two arguments, which are the DN
   * of the branch entry and the name of the undefined template.
   */
  public static final int MSGID_MAKELDIF_UNDEFINED_BRANCH_SUBORDINATE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 543;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load a class for use as a tag.  This takes a single argument,
   * which is the name of the class.
   */
  public static final int MSGID_MAKELDIF_CANNOT_LOAD_TAG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 544;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to instantiate a class for use as a tag.  This takes a single
   * argument, which is the name of the class.
   */
  public static final int MSGID_MAKELDIF_CANNOT_INSTANTIATE_TAG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 545;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * register a new tag with a conflicting name.  This takes two arguments,
   * which are the name of the class and the name of the tag.
   */
  public static final int MSGID_MAKELDIF_CONFLICTING_TAG_NAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 546;



  /**
   * The message ID for the message that will be used if a potential undefined
   * constant is used in the template file.  This takes two arguments, which are
   * the name of the constant and the line number on which it is used.
   */
  public static final int MSGID_MAKELDIF_WARNING_UNDEFINED_CONSTANT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 547;



  /**
   * The message ID for the message that will be used if a constant definition
   * does not include an equal sign.  This takes a single argument, which is the
   * line number on which it appears.
   */
  public static final int MSGID_MAKELDIF_DEFINE_MISSING_EQUALS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 548;



  /**
   * The message ID for the message that will be used if a constant definition
   * does not include a name.  This takes a single argument, which is the line
   * number on which it appears.
   */
  public static final int MSGID_MAKELDIF_DEFINE_NAME_EMPTY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 549;



  /**
   * The message ID for the message that will be used if a constant definition
   * has a name that conflicts with another constant.  This takes two arguments,
   * which are the name of the constant and the line number on which the
   * conflict was detected.
   */
  public static final int MSGID_MAKELDIF_CONFLICTING_CONSTANT_NAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 550;



  /**
   * The message ID for the message that will be used if a constant definition
   * does not have a value.  This takes two arguments, which are the name of the
   * constant and the line number on which it is defined.
   */
  public static final int MSGID_MAKELDIF_WARNING_DEFINE_VALUE_EMPTY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 551;



  /**
   * The message ID for the message that will be used if a branch definition
   * has a DN that conflicts with another branch.  This takes two arguments,
   * which are the branch DN and the line number on which the conflict was
   * detected.
   */
  public static final int MSGID_MAKELDIF_CONFLICTING_BRANCH_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 552;



  /**
   * The message ID for the message that will be used if a template definition
   * has a name that conflicts with another template.  This takes two arguments,
   * which are the template name and the line number on which the conflict was
   * detected.
   */
  public static final int MSGID_MAKELDIF_CONFLICTING_TEMPLATE_NAME =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 553;



  /**
   * The message ID for the message that will be used if an unrecognized line
   * was found in the template file.  This takes two arguments, which are the
   * content of the line and the line number on which it was found.
   */
  public static final int MSGID_MAKELDIF_UNEXPECTED_TEMPLATE_FILE_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 554;



  /**
   * The message ID for the message that will be used if a template references
   * an undefined subordinate template.  This takes two arguments, which are the
   * name of the parent template and the name of the undefined subordinate
   * template.
   */
  public static final int MSGID_MAKELDIF_UNDEFINED_TEMPLATE_SUBORDINATE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 555;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the branch DN.  This takes two arguments, which are the DN
   * string and the line number on which it appears.
   */
  public static final int MSGID_MAKELDIF_CANNOT_DECODE_BRANCH_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 556;



  /**
   * The message ID for the message that will be used if a subordinate template
   * definition does not include a colon.  This takes two arguments, which are
   * the line number and the branch DN.
   */
  public static final int MSGID_MAKELDIF_BRANCH_SUBORDINATE_TEMPLATE_NO_COLON =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 557;



  /**
   * The message ID for the message that will be used if a subordinate template
   * specifies an invalid number of entries.  This takes four arguments, which
   * are the line number, the branch DN, the invalid number of entries, and the
   * subordinate template name.
   */
  public static final int
       MSGID_MAKELDIF_BRANCH_SUBORDINATE_INVALID_NUM_ENTRIES =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 558;



  /**
   * The message ID for the message that will be used if there are zero entries
   * to be created for a subordinate template.  This takes three arguments,
   * which are the line number, the branch DN, and the subordinate template
   * name.
   */
  public static final int MSGID_MAKELDIF_BRANCH_SUBORDINATE_ZERO_ENTRIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 559;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the number of entries for a subordinate template.  This
   * takes three arguments, which are the template name, the line number, and
   * the branch DN.
   */
  public static final int
       MSGID_MAKELDIF_BRANCH_SUBORDINATE_CANT_PARSE_NUMENTRIES =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 560;



  /**
   * The message ID for the message that will be used if a subordinate template
   * definition does not include a colon.  This takes two arguments, which are
   * the line number and the template name.
   */
  public static final int
       MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_TEMPLATE_NO_COLON =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 561;



  /**
   * The message ID for the message that will be used if a subordinate template
   * specifies an invalid number of entries.  This takes four arguments, which
   * are the line number, the template name, the invalid number of entries, and
   * the subordinate template name.
   */
  public static final int
       MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_INVALID_NUM_ENTRIES =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 562;



  /**
   * The message ID for the message that will be used if there are zero entries
   * to be created for a subordinate template.  This takes three arguments,
   * which are the line number, the template name, and the subordinate template
   * name.
   */
  public static final int MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_ZERO_ENTRIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 563;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the number of entries for a subordinate template.  This
   * takes three arguments, which are the subordinate template name, the line
   * number, and the template name.
   */
  public static final int
       MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_CANT_PARSE_NUMENTRIES =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 564;



  /**
   * The message ID for the message that will be used if a template references
   * an undefined RDN attribute.  This takes two arguments, which are the
   * name of the parent template and the name of the undefined RDN attribute.
   */
  public static final int MSGID_MAKELDIF_TEMPLATE_MISSING_RDN_ATTR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 565;



  /**
   * The message ID for the message that will be used if a branch definition has
   * an extra line with no colon to separate the attribute type from the value
   * pattern.  This takes two arguments, which are the line number in the
   * template file and the branch DN.
   */
  public static final int MSGID_MAKELDIF_NO_COLON_IN_BRANCH_EXTRA_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 566;



  /**
   * The message ID for the message that will be used if a branch definition has
   * an extra line with no attribute type .  This takes two arguments, which are
   * the line number in the  template file and the branch DN.
   */
  public static final int MSGID_MAKELDIF_NO_ATTR_IN_BRANCH_EXTRA_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 567;



  /**
   * The message ID for the message that will be used if a branch definition has
   * an extra line with no value.  This takes two arguments, which are the line
   * number in the  template file and the branch DN.
   */
  public static final int MSGID_MAKELDIF_NO_VALUE_IN_BRANCH_EXTRA_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 568;



  /**
   * The message ID for the message that will be used if a template definition
   * has a line with no colon to separate the attribute type from the value
   * pattern.  This takes two arguments, which are the line number in the
   * template file and the template name.
   */
  public static final int MSGID_MAKELDIF_NO_COLON_IN_TEMPLATE_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 569;



  /**
   * The message ID for the message that will be used if a template definition
   * has a line with no attribute type .  This takes two arguments, which are
   * the line number in the  template file and the template name.
   */
  public static final int MSGID_MAKELDIF_NO_ATTR_IN_TEMPLATE_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 570;



  /**
   * The message ID for the message that will be used if a template definition
   * has a line with no value.  This takes two arguments, which are the line
   * number in the  template file and the template name.
   */
  public static final int MSGID_MAKELDIF_NO_VALUE_IN_TEMPLATE_LINE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 571;



  /**
   * The message ID for the message that will be used if a template definition
   * references an undefined tag.  This takes two arguments, which are the name
   * of the tag and the line number on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_NO_SUCH_TAG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 572;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a new tag instance.  This takes three arguments, which are
   * the tag name, the line number, and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_MAKELDIF_CANNOT_INSTANTIATE_NEW_TAG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 573;


  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 574;


  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 575;


  /**
   * The message ID for the message that will be used as the description of the
   * templateFile argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_TEMPLATE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 576;


  /**
   * The message ID for the message that will be used as the description of the
   * ldifFile argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 577;


  /**
   * The message ID for the message that will be used as the description of the
   * randomSeed argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_SEED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 578;


  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_HELP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 579;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 580;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 581;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server JMX subsystem.  This takes two arguments,
   * which are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_CANNOT_INITIALIZE_JMX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 582;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server configuration.  This takes two
   * arguments, which are the path to the Directory Server configuration file
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_CANNOT_INITIALIZE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 583;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server schema.  This takes two arguments, which
   * are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_CANNOT_INITIALIZE_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 584;



  /**
   * The message ID for the message that will be used if an I/O exception was
   * thrown while trying to load the template file.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_IOEXCEPTION_DURING_PARSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 585;



  /**
   * The message ID for the message that will be used if a non-I/O exception was
   * thrown while trying to load the template file.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_EXCEPTION_DURING_PARSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 586;



  /**
   * The message ID for the message that will be used if a value cannot be
   * parsed as a format string.  This takes three arguments, which are the
   * provided value, the tag name, and the line number on which it appears in
   * the  template file.
   */
  public static final int MSGID_MAKELDIF_TAG_INVALID_FORMAT_STRING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 587;



  /**
   * The message ID for the message that will be used if a random tag does not
   * specify the random value type.  This takes a single argument, which is the
   * line number on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_NO_RANDOM_TYPE_ARGUMENT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 588;



  /**
   * The message ID for the message that will be used if a random tag will
   * always generate an empty value.  This takes a single argument, which is the
   * line number on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_WARNING_EMPTY_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_WARNING | 589;



  /**
   * The message ID for the message that will be used if a random tag has an
   * unknown random type.  This takes two arguments, which are the line number
   * on which it appears in the template file and the provided random type.
   */
  public static final int MSGID_MAKELDIF_TAG_UNKNOWN_RANDOM_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 590;



  /**
   * The message ID for the message that will be used as the description of the
   * resourcePath argument.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_DESCRIPTION_RESOURCE_PATH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 591;



  /**
   * The message ID for the message that will be used if the specified temlate
   * file could not be found.  This takes a single argument, which is the
   * specified template file.
   */
  public static final int MSGID_MAKELDIF_COULD_NOT_FIND_TEMPLATE_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 592;



  /**
   * The message ID for the message that will be used if the specified resource
   * directory does not exist.  This takes a single argument, which is the
   * specified resource directory.
   */
  public static final int MSGID_MAKELDIF_NO_SUCH_RESOURCE_DIRECTORY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 593;



  /**
   * The message ID for the message that will be used if the specified resource
   * directory exists but is not a directory.  This takes a single argument,
   * which is the specified resource directory.
   */
  public static final int MSGID_MAKELDIF_RESOURCE_DIRECTORY_NOT_DIRECTORY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 594;



  /**
   * The message ID for the message that will be used if the specified tag
   * could not be found.  This takes three arguments, which are the file path,
   * the tag name, and the line number on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_CANNOT_FIND_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 595;



  /**
   * The message ID for the message that will be used if an invalid file access
   * mode was specified.  This takes three arguments, which are the access mode,
   * the tag name, and the line number on which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_INVALID_FILE_ACCESS_MODE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 596;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the contents of a file.  This takes four arguments, which
   * are the file path, the tag name, the line number on which it appears in the
   * template file, and a message explaining the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_TAG_CANNOT_READ_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 597;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the output LDIF file.  This takes two arguments, which are
   * the path to the LDIF file and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_MAKELDIF_UNABLE_TO_CREATE_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 598;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write to the output LDIF file.  This takes two arguments, which
   * are the path to the LDIF file and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_MAKELDIF_ERROR_WRITING_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 599;



  /**
   * The message ID for the message that will be used to indicate the number of
   * entries that have been processed.  This takes a single argument, which is
   * the number of entries processed.
   */
  public static final int MSGID_MAKELDIF_PROCESSED_N_ENTRIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 600;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write an entry.  This takes two arguments, which are the DN of
   * the entry and a message with information about the problem that occurred.
   */
  public static final int MSGID_MAKELDIF_CANNOT_WRITE_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 601;



  /**
   * The message ID for the message that will be used to indicate that all
   * processing has been completed.  This takes a single argument, which is the
   * number of entries processed.
   */
  public static final int MSGID_MAKELDIF_PROCESSING_COMPLETE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 602;



  /**
   * The message ID for the message that will be used as the description of the
   * templateFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_TEMPLATE_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 603;



  /**
   * The message ID for the message that will be used if both the ldifFile and
   * the templateFile arguments were provided.  This takes two arguments, which
   * are the long identifiers for the ldifFile and templateFile options.
   */
  public static final int MSGID_LDIFIMPORT_CONFLICTING_OPTIONS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 604;



  /**
   * The message ID for the message that will be used if neither the ldifFile or
   * the templateFile arguments was provided.  This takes two arguments, which
   * are the long identifiers for the ldifFile and templateFile options.
   */
  public static final int MSGID_LDIFIMPORT_MISSING_REQUIRED_ARGUMENT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 605;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize a MakeLDIF template file.  This takes two
   * arguments, which are the path to the template file and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_LDIFIMPORT_CANNOT_PARSE_TEMPLATE_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 606;



  /**
   * The message ID for the message that will be used if a template line has an
   * unclosed tag.  This takes a single argument, which is the line number on
   * which it appears in the template file.
   */
  public static final int MSGID_MAKELDIF_INCOMPLETE_TAG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 607;



  /**
   * The message ID for the message that will be used if a branch definition
   * includes a tag that is not allowed for use in branches.  This takes two
   * arguments, which are the name of the tag and the line number on which it
   * appears in the template file.
   */
  public static final int MSGID_MAKELDIF_TAG_NOT_ALLOWED_IN_BRANCH =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 608;



  /**
   * The message ID for the message that will be used as the description of the
   * randomSeed argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_DESCRIPTION_RANDOM_SEED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 609;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry twice in the same set of changes.  This takes a single
   * argument, which is the DN of the entry.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_ADD_ENTRY_TWICE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 610;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * delete an entry that had just been added in the same set of changes.  This
   * takes a single argument, which is the DN of the entry.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_DELETE_AFTER_ADD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 611;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * modify an entry that had just been added or deleted in the same set of
   * changes.  This takes a single argument, which is the DN of the entry.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_MODIFY_ADDED_OR_DELETED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 612;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify DN operation.  This takes a single argument, which is the
   * DN of the entry.
   */
  public static final int MSGID_LDIFMODIFY_MODDN_NOT_SUPPORTED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 613;



  /**
   * The message ID for the message that will be used if a change record has an
   * unknown changetype.  This takes two arguments, which are the DN of the
   * entry and the specified changetype.
   */
  public static final int MSGID_LDIFMODIFY_UNKNOWN_CHANGETYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 614;



  /**
   * The message ID for the message that will be used if an entry to be added
   * already exists in the data.  This takes a single argument, which is the DN
   * of the entry.
   */
  public static final int MSGID_LDIFMODIFY_ADD_ALREADY_EXISTS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 615;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * deleted because it does not exist in the data set.  This takes a single
   * argument, which is the DN of the entry.
   */
  public static final int MSGID_LDIFMODIFY_DELETE_NO_SUCH_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 616;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * modified because it does not exist in the data set.  This takes a single
   * argument, which is the DN of the entry.
   */
  public static final int MSGID_LDIFMODIFY_MODIFY_NO_SUCH_ENTRY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 617;


  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 618;


  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 619;



  /**
   * The message ID for the message that will be used as the description for the
   * sourceLDIF argument.  It does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_DESCRIPTION_SOURCE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 620;



  /**
   * The message ID for the message that will be used as the description for the
   * changesLDIF argument.  It does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_DESCRIPTION_CHANGES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 621;



  /**
   * The message ID for the message that will be used as the description for the
   * targetLDIF argument.  It does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_DESCRIPTION_TARGET =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 622;



  /**
   * The message ID for the message that will be used as the description for the
   * help argument.  It does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_DESCRIPTION_HELP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 623;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 624;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFMODIFY_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 625;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server JMX subsystem.  This takes two arguments,
   * which are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_INITIALIZE_JMX =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 626;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server configuration.  This takes two
   * arguments, which are the path to the Directory Server configuration file
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_INITIALIZE_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 627;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the Directory Server schema.  This takes two arguments, which
   * are the path to the Directory Server configuration file and a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_INITIALIZE_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 628;



  /**
   * The message ID for the message that will be used if the source LDIF file
   * does not exist.  This takes a single argument, which is the path to the
   * source LDIF file.
   */
  public static final int MSGID_LDIFMODIFY_SOURCE_DOES_NOT_EXIST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 629;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the source LDIF file.  This takes two arguments, which are
   * the path to the source LDIF file and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_OPEN_SOURCE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 630;



  /**
   * The message ID for the message that will be used if the changes LDIF file
   * does not exist.  This takes a single argument, which is the path to the
   * changes LDIF file.
   */
  public static final int MSGID_LDIFMODIFY_CHANGES_DOES_NOT_EXIST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 631;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the changes LDIF file.  This takes two arguments, which are
   * the path to the changes LDIF file and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_OPEN_CHANGES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 632;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the target LDIF file.  This takes two arguments, which are
   * the path to the target LDIF file and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_LDIFMODIFY_CANNOT_OPEN_TARGET =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 633;



  /**
   * The message ID for the message that will be used if an error occurs while
   * processing the changes.  This takes a single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_LDIFMODIFY_ERROR_PROCESSING_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 634;



  /**
   * The message ID for the message that will be used as the description for the
   * host argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_HOST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 635;



  /**
   * The message ID for the message that will be used as the description for the
   * port argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_PORT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 636;



  /**
   * The message ID for the message that will be used as the description for the
   * bindDN argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_BIND_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 637;



  /**
   * The message ID for the message that will be used as the description for the
   * bindPW argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_BIND_PW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 638;



  /**
   * The message ID for the message that will be used as the description for the
   * bindPWFile argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_BIND_PW_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 639;



  /**
   * The message ID for the message that will be used as the description for the
   * authzID argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_AUTHZID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 640;



  /**
   * The message ID for the message that will be used as the description for the
   * provideDNForAuthzID argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_PROVIDE_DN_FOR_AUTHZID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 641;



  /**
   * The message ID for the message that will be used as the description for the
   * newPW argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_NEWPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 642;



  /**
   * The message ID for the message that will be used as the description for the
   * newPWFile argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_NEWPWFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 643;



  /**
   * The message ID for the message that will be used as the description for the
   * currentPW argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_CURRENTPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 644;



  /**
   * The message ID for the message that will be used as the description for the
   * currentPWFile argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 645;



  /**
   * The message ID for the message that will be used as the description for the
   * useSSL argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_USE_SSL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 646;



  /**
   * The message ID for the message that will be used as the description for the
   * useStartTLS argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_USE_STARTTLS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 647;



  /**
   * The message ID for the message that will be used as the description for the
   * blindTrust argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_BLIND_TRUST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 648;



  /**
   * The message ID for the message that will be used as the description for the
   * sslKeyStore argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 649;



  /**
   * The message ID for the message that will be used as the description for the
   * sslKeyStorePINFile argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE_PINFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 650;



  /**
   * The message ID for the message that will be used as the description for the
   * sslTrustStore argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 651;



  /**
   * The message ID for the message that will be used as the description for the
   * sslTrustStorePINFile argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PINFILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 652;



  /**
   * The message ID for the message that will be used as the description for the
   * help argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_USAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 653;



  /**
   * The message ID for the message that will be used if an error occurs while
   * initializing the command-line arguments.  This takes a single argument,
   * which is an explanation of the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 654;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the command-line arguments.  This takes a single argument, which is
   * an explanation of the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 655;



  /**
   * The message ID for the message that will be used to indicate that two
   * arguments must not be used together.  This takes two arguments, which are
   * the long identifiers for the conflicting arguments.
   */
  public static final int MSGID_LDAPPWMOD_CONFLICTING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 656;



  /**
   * The message ID for the message that will be used to indicate that the
   * bind DN and password must be provided together.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 657;



  /**
   * The message ID for the message that will be used to indicate that an
   * anonymous modification requires the authorization ID and the current
   * password.  This does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_ANON_REQUIRES_AUTHZID_AND_CURRENTPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 658;



  /**
   * The message ID for the message that will be used to indicate that if a
   * particular argument is present, then another argument is also required.
   * This takes two arguments, which are the long identifier of the first
   * argument, and the long identifier of the argument that it requires.
   */
  public static final int MSGID_LDAPPWMOD_DEPENDENT_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 659;



  /**
   * The message ID for the message that will be used to indicate that an error
   * occurred while initializing the SSL/TLS subsystem.  It takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_ERROR_INITIALIZING_SSL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 660;



  /**
   * The message ID for the message that will be used to indicate that an error
   * occurred while connecting to the server.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_CANNOT_CONNECT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 661;



  /**
   * The message ID for the message that will be used to indicate that an error
   * occurred while trying to send the password modify request.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_CANNOT_SEND_PWMOD_REQUEST =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 662;



  /**
   * The message ID for the message that will be used to indicate that an error
   * occurred while trying to read the password modify response.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_CANNOT_READ_PWMOD_RESPONSE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 663;



  /**
   * The message ID for the message that will be used to indicate that the
   * password modify operation failed.  This takes a single argument, which is
   * the LDAP result code.
   */
  public static final int MSGID_LDAPPWMOD_FAILED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 664;



  /**
   * The message ID for the message that will be used to provide the error
   * message from the password modify response.  This takes a single argument,
   * which is the error message string.
   */
  public static final int MSGID_LDAPPWMOD_FAILURE_ERROR_MESSAGE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 665;



  /**
   * The message ID for the message that will be used to provide the matched DN
   * from the password modify response.  This takes a single argument, which is
   * the matched DN.
   */
  public static final int MSGID_LDAPPWMOD_FAILURE_MATCHED_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 666;



  /**
   * The message ID for the message that will be used to indicate that the
   * password modify operation was successful.  This does not take any
   * arguments.
   */
  public static final int MSGID_LDAPPWMOD_SUCCESSFUL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 667;



  /**
   * The message ID for the message that will be used to provide additional
   * information from the successful password modify response.  This takes a
   * single argument, which is the additional information string.
   */
  public static final int MSGID_LDAPPWMOD_ADDITIONAL_INFO =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 668;



  /**
   * The message ID for the message that will be used to provide the generated
   * password from the password modify response.  This takes a single argument,
   * which is the generated password.
   */
  public static final int MSGID_LDAPPWMOD_GENERATED_PASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 669;



  /**
   * The message ID for the message that will be used if the password modify
   * response contained an element with an unrecognized BER type.  This takes
   * a single argument, which is a hex representation of the BER type.
   */
  public static final int MSGID_LDAPPWMOD_UNRECOGNIZED_VALUE_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 670;



  /**
   * The message ID for the message that will be used if an error occurred while
   * attempting to decode the password modify response value.  It takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LDAPPWMOD_COULD_NOT_DECODE_RESPONSE_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 671;



  /**
   * The message ID for the message that will be used to indicate that the
   * LDIF import was unsuccessful.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_IMPORT_UNSUCCESSFUL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 672;



  /**
   * The message ID for the message that will be used if an assertion value that
   * is supposed to be base64-encoded can't be decoded.  This does not take any
   * arguments.
   */
  public static final int MSGID_COMPARE_CANNOT_BASE64_DECODE_ASSERTION_VALUE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 673;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the assertion value from a file.  This takes a single
   * argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_COMPARE_CANNOT_READ_ASSERTION_VALUE_FROM_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 674;



  /**
   * The message ID for the message that will be used as the description for the
   * targetFile argument.  It does not take any arguments.
   */
  public static final int MSGID_WAIT4DEL_DESCRIPTION_TARGET_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 675;



  /**
   * The message ID for the message that will be used as the description for the
   * logFile argument.  It does not take any arguments.
   */
  public static final int MSGID_WAIT4DEL_DESCRIPTION_LOG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 676;



  /**
   * The message ID for the message that will be used as the description for the
   * timeout argument.  It does not take any arguments.
   */
  public static final int MSGID_WAIT4DEL_DESCRIPTION_TIMEOUT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 677;



  /**
   * The message ID for the message that will be used as the description for the
   * help argument.  It does not take any arguments.
   */
  public static final int MSGID_WAIT4DEL_DESCRIPTION_HELP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 678;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_WAIT4DEL_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 679;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_WAIT4DEL_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 680;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the log file for reading.  This takes two arguments, which
   * are the path to the log file and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_WAIT4DEL_CANNOT_OPEN_LOG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 681;



  /**
   * The message ID for the message that will be used if an attempt was made to
   * use LDAPCompare without any entry DNs.  This does not take any arguments.
   */
  public static final int MSGID_LDAPCOMPARE_NO_DNS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 682;



  /**
   * The message ID for the message that will be used as the description for the
   * backup tool.  This does not take any arguments.
   */
  public static final int MSGID_BACKUPDB_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 683;



  /**
   * The message ID for the message that will be used as the description for the
   * configure-ds tool.  This does not take any arguments.
   */
  public static final int MSGID_CONFIGDS_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 684;



  /**
   * The message ID for the message that will be used as the description for the
   * encode-password tool.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 685;



  /**
   * The message ID for the message that will be used as the description for the
   * export-ldif tool.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 686;



  /**
   * The message ID for the message that will be used as the description for the
   * import-ldif tool.  This does not take any arguments.
   */
  public static final int MSGID_LDIFIMPORT_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 687;



  /**
   * The message ID for the message that will be used as the description for the
   * setup tool.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 688;



  /**
   * The message ID for the message that will be used as the description for the
   * ldapcompare tool.  This does not take any arguments.
   */
  public static final int MSGID_LDAPCOMPARE_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 689;



  /**
   * The message ID for the message that will be used as the description for the
   * ldapdelete tool.  This does not take any arguments.
   */
  public static final int MSGID_LDAPDELETE_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 690;



  /**
   * The message ID for the message that will be used as the description for the
   * ldapmodify tool.  This does not take any arguments.
   */
  public static final int MSGID_LDAPMODIFY_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 691;



  /**
   * The message ID for the message that will be used as the description for the
   * ldappasswordmodify tool.  This does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 692;



  /**
   * The message ID for the message that will be used as the description for the
   * ldapsearch tool.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 693;



  /**
   * The message ID for the message that will be used as the description for the
   * ldif-diff tool.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 694;



  /**
   * The message ID for the message that will be used as the description for the
   * ldifmodify tool.  This does not take any arguments.
   */
  public static final int MSGID_LDIFMODIFY_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 695;



  /**
   * The message ID for the message that will be used as the description for the
   * ldifsearch tool.  This does not take any arguments.
   */
  public static final int MSGID_LDIFSEARCH_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 696;



  /**
   * The message ID for the message that will be used as the description for the
   * makeldif tool.  This does not take any arguments.
   */
  public static final int MSGID_MAKELDIF_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 697;



  /**
   * The message ID for the message that will be used as the description for the
   * restore tool.  This does not take any arguments.
   */
  public static final int MSGID_RESTOREDB_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 698;



  /**
   * The message ID for the message that will be used as the description for the
   * stop-ds tool.  This does not take any arguments.
   */
  public static final int MSGID_STOPDS_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 699;



  /**
   * The message ID for the message that will be used as the description for the
   * verify-index tool.  This does not take any arguments.
   */
  public static final int MSGID_VERIFYINDEX_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 700;



  /**
   * The message ID for the message that will be used as the description for the
   * wait-for-delete tool.  This does not take any arguments.
   */
  public static final int MSGID_WAIT4DEL_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 701;



  /**
   * The message ID for the message that will be used if two conflicting
   * command-line arguments were provided.  This takes two arguments, which are
   * the long names for the conflicting arguments.
   */
  public static final int MSGID_TOOL_CONFLICTING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 702;



  /**
   * The message ID for the message that will be used if no compare attribute
   * was provided.  This does not take any arguments.
   */
  public static final int MSGID_LDAPCOMPARE_NO_ATTR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 703;



  /**
   * The message ID for the message that will be used if the attribute string
   * had an invalid format.  This takes a single argument, which is the invalid
   * attribute string.
   */
  public static final int MSGID_LDAPCOMPARE_INVALID_ATTR_STRING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 704;



  /**
   * The message ID for the message that will be used if the control string
   * had an invalid format.  This takes a single argument, which is the invalid
   * control string.
   */
  public static final int MSGID_TOOL_INVALID_CONTROL_STRING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 705;



  /**
   * The message ID for the message that will be used if the user requested SASL
   * EXTERNAL authentication but is not using SSL or StartTLS.  This does not
   * take any arguments.
   */
  public static final int MSGID_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 706;



  /**
   * The message ID for the message that will be used if the user requested SASL
   * EXTERNAL authentication but did not specify a keystore path.  This does not
   * take any arguments.
   */
  public static final int MSGID_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 707;



  /**
   * The message ID for the message that will be used to provide the persistent
   * search change type.  This takes a single argument, which is the change type
   * string.
   */
  public static final int MSGID_LDAPSEARCH_PSEARCH_CHANGE_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 708;



  /**
   * The message ID for the message that will be used to provide the persistent
   * previous entry DN.  This takes a single argument, which is the previous DN
   * string.
   */
  public static final int MSGID_LDAPSEARCH_PSEARCH_PREVIOUS_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 709;



  /**
   * The message ID for the message that will be used to provide the header for
   * the account usability control output.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_HEADER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 710;



  /**
   * The message ID for the message that will be used to indicate that an
   * account is usable.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_IS_USABLE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 711;



  /**
   * The message ID for the message that will be used to indicate the length of
   * time before the password expires.  This takes a single argument, which is a
   * human-readable string representation of the remaining time.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 712;



  /**
   * The message ID for the message that will be used to indicate that the
   * account is not usable.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_NOT_USABLE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 713;



  /**
   * The message ID for the message that will be used to indicate that the
   * account has been deactivated.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_ACCT_INACTIVE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 714;



  /**
   * The message ID for the message that will be used to indicate that the
   * user's password has been reset and must be changed before anything else can
   * be done.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_PW_RESET =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 715;



  /**
   * The message ID for the message that will be used to indicate that the
   * user's password has expired.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_PW_EXPIRED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 716;



  /**
   * The message ID for the message that will be used to provide the number of
   * grace logins remaining for the user.  This takes a single argument, which
   * is the number of remaining grace logins.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 717;



  /**
   * The message ID for the message that will be used to indicate that the
   * account has been locked.  This does not take any arguments.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_LOCKED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 718;



  /**
   * The message ID for the message that will be used to provide the length of
   * time until the account is automatically unlocked.  This takes a single
   * argument, which is a human-readable string representation of the time
   * remaining.
   */
  public static final int MSGID_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 719;



  /**
   * The message ID for the message that will be used as the description of the
   * keystorePasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_KEYSTOREPASSWORD_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 720;



  /**
   * The message ID for the message that will be used as the description of the
   * truststorePassword argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_TRUSTSTOREPASSWORD =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 721;



  /**
   * The message ID for the message that will be used as the description of the
   * truststorePasswordFile argument.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_TRUSTSTOREPASSWORD_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 722;



  /**
   * The message ID for the message that will be used as the description for the
   * list-backends tool.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_TOOL_DESCRIPTION =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 723;


  /**
   * The message ID for the message that will be used as the description of the
   * configClass argument.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_DESCRIPTION_CONFIG_CLASS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 724;


  /**
   * The message ID for the message that will be used as the description of the
   * configFile argument.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_DESCRIPTION_CONFIG_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 725;


  /**
   * The message ID for the message that will be used as the description of the
   * backendID argument.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_DESCRIPTION_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 726;


  /**
   * The message ID for the message that will be used as the description of the
   * baseDN argument.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_DESCRIPTION_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 727;


  /**
   * The message ID for the message that will be used as the description of the
   * help argument.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_DESCRIPTION_HELP =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 728;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the command-line argument parser.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LISTBACKENDS_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 729;



  /**
   * The message ID for the message that will be used if an error occurs while
   * parsing the provided command-line arguments.  This takes a single argument,
   * which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LISTBACKENDS_ERROR_PARSING_ARGS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 730;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to bootstrap the Directory Server.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_LISTBACKENDS_SERVER_BOOTSTRAP_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 731;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying load the Directory Server configuration.  This takes a single
   * argument which is a message with information about the problem that
   * occurred.
   */
  public static final int MSGID_LISTBACKENDS_CANNOT_LOAD_CONFIG =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 732;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load the Directory Server schema.  This takes a single argument,
   * which is a message with information about the problem that occurred.
   */
  public static final int MSGID_LISTBACKENDS_CANNOT_LOAD_SCHEMA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 733;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read backend information from the configuration.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_LISTBACKENDS_CANNOT_GET_BACKENDS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 734;



  /**
   * The message ID for the message that will be used if a provided string can't
   * be parsed as a DN.  This takes two arguments, which are the provided string
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_LISTBACKENDS_INVALID_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 735;



  /**
   * The message ID for the message that will be used to indicate that a
   * provided DN was not a base DN for any backend.  This takes a single
   * argument, which is the provided DN.
   */
  public static final int MSGID_LISTBACKENDS_NOT_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 736;



  /**
   * The message ID for the message that will be used to indicate that a
   * provided DN was not appropriate for any backend.  This takes a single
   * argument, which is the provided DN.
   */
  public static final int MSGID_LISTBACKENDS_NO_BACKEND_FOR_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 737;



  /**
   * The message ID for the message that will be used to indicate that a
   * provided DN was below the base DN for a backend in the server.  This takes
   * three arguments, which are the provided DN, the base DN for the
   * corresponding backend, and the backend ID.
   */
  public static final int MSGID_LISTBACKENDS_DN_BELOW_BASE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 738;



  /**
   * The message ID for the message that will be used to indicate that a
   * provided DN a base DN for a backend.  This takes two arguments, which are
   * the provided DN and the corresponding backend ID.
   */
  public static final int MSGID_LISTBACKENDS_BASE_FOR_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 739;



  /**
   * The message ID for the message that will be used as the label for the
   * backend ID column header.
   */
  public static final int MSGID_LISTBACKENDS_LABEL_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 740;



  /**
   * The message ID for the message that will be used as the label for the base
   * DN column header.
   */
  public static final int MSGID_LISTBACKENDS_LABEL_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 741;



  /**
   * The message ID for the message that will be used if the provided backend ID
   * is not associated with any backend configured in the server.  This takes a
   * single argument, which is the provided backend ID.
   */
  public static final int MSGID_LISTBACKENDS_NO_SUCH_BACKEND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 742;



  /**
   * The message ID for the message that will be used if none of the provided
   * backend IDs are valid.  This does not take any arguments.
   */
  public static final int MSGID_LISTBACKENDS_NO_VALID_BACKENDS =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 743;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the base DN for backend configuration entries.  This takes
   * two arguments, which are the base DN and a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_LISTBACKENDS_CANNOT_DECODE_BACKEND_BASE_DN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 744;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to retrieve the backend configuration base entry.  This takes two
   * arguments, which are the DN of the entry and a message explaining the
   * problem that occurred.
   */
  public static final int
       MSGID_LISTBACKENDS_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 745;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backend ID.  This takes two arguments, which are
   * the DN of the backend configuration entry and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_LISTBACKENDS_CANNOT_DETERMINE_BACKEND_ID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 746;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the set of base DNs for a backend.  This takes two
   * arguments, which are the DN of the backend configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int
       MSGID_LISTBACKENDS_CANNOT_DETERMINE_BASES_FOR_BACKEND =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 747;



  /**
   * The message ID for the message that will be used if the encoded password is
   * not valid according to the user password syntax.  This takes a single
   * argument, which is a message explaining why it is invalid.
   */
  public static final int MSGID_ENCPW_INVALID_ENCODED_USERPW =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 748;



  /**
   * The message ID for the message that will be used as the description of the
   * useCompareResultCode argument.  This does not take any arguments.
   */
  public static final int MSGID_ENCPW_DESCRIPTION_USE_COMPARE_RESULT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 749;



  /**
   * The message ID for the message that will be used as the description of the
   * countEntries property.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_COUNT_ENTRIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 750;



  /**
   * The message ID for the message that will be used to provide the number of
   * matching entries for a search request.  This takes a single argument, which
   * is the number of matching entries.
   */
  public static final int MSGID_LDAPSEARCH_MATCHING_ENTRY_COUNT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 751;



  /**
   * The message ID for the message that will be used as the description for the
   * cli argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_CLI =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 752;



  /**
   * The message ID for the message that will be used as the description for the
   * sampleData argument.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_DESCRIPTION_SAMPLE_DATA =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 753;



  /**
   * The message ID for the message that will be used as the heading when asking
   * the user how to populate the database.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_HEADER_POPULATE_TYPE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 754;



  /**
   * The message ID for the message that will be used as the text for the
   * populate option that will create only the base entry.  This does not take
   * any arguments.
   */
  public static final int MSGID_INSTALLDS_POPULATE_OPTION_BASE_ONLY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 755;



  /**
   * The message ID for the message that will be used as the text for the
   * populate option that will leave the database emtpy.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_POPULATE_OPTION_LEAVE_EMPTY =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 756;



  /**
   * The message ID for the message that will be used as the text for the
   * populate option that will import data from LDIF.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_POPULATE_OPTION_IMPORT_LDIF =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 757;



  /**
   * The message ID for the message that will be used as the text for the
   * populate option that will create only the base entry.  This does not take
   * any arguments.
   */
  public static final int MSGID_INSTALLDS_POPULATE_OPTION_GENERATE_SAMPLE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 758;



  /**
   * The message ID for the message that will be used as the text for the prompt
   * asking the user how to populate the database.  This does not take any
   * arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_POPULATE_CHOICE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 759;



  /**
   * The message ID for the message that will be used to indicate that the
   * specified LDIF file does not exist.  This takes a single argument, which is
   * the specified LDIF file.
   */
  public static final int MSGID_INSTALLDS_NO_SUCH_LDIF_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 780;



  /**
   * The message ID for the message that will be used as the prompt for the
   * number of entries to generate.  This does not take any arguments.
   */
  public static final int MSGID_INSTALLDS_PROMPT_NUM_ENTRIES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 781;



  /**
   * The message ID for the message that will be used if an error occurs when
   * trying to create the template for generating sample data.  This takes a
   * single argument, which is a message explaining the problem that occurred.
   */
  public static final int MSGID_INSTALLDS_CANNOT_CREATE_TEMPLATE_FILE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 782;



  /**
   * The message ID for the message that will be used as the description for the
   * sslKeyStorePIN argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE_PIN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 783;



  /**
   * The message ID for the message that will be used as the description for the
   * sslKeyStorePIN argument.  It does not take any arguments.
   */
  public static final int MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PIN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 784;



  /**
   * The message ID for the message that will be used as the description of the
   * excludeOperational argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_OPERATIONAL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 785;



  /**
   * The message ID for the message that will be used to display warning
   * information included in the password policy response control.  This takes
   * two arguments, which are the string representation of the warning type and
   * the integer warning value.
   */
  public static final int MSGID_LDAPPWMOD_PWPOLICY_WARNING =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 786;



  /**
   * The message ID for the message that will be used to display error
   * information included in the password policy response control.  This takes
   * a single argument, which is the string representation of the error type.
   */
  public static final int MSGID_LDAPPWMOD_PWPOLICY_ERROR =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 787;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the password policy response control.  This takes a
   * single argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_LDAPPWMOD_CANNOT_DECODE_PWPOLICY_CONTROL =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_MILD_ERROR | 788;



  /**
   * The message ID for the message that will be used if the connection to the
   * Directory Server is closed while attempting to read the bind response from
   * the server.  This does not take any arguments.
   */
  public static final int
       MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE =
            CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 789;



  /**
   * The message ID for the message that will be used as the description for the
   * command-line option that includes the simple paged results control in the
   * request.  This does not take any arguments.
   */
  public static final int MSGID_DESCRIPTION_SIMPLE_PAGE_SIZE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 790;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * use the simple paged results control in conjunction with multiple search
   * filters.  This does not take any arguments.
   */
  public static final int MSGID_PAGED_RESULTS_REQUIRES_SINGLE_FILTER =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 791;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode the simple paged results response control from the
   * server.  This takes a single argument, which is a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_PAGED_RESULTS_CANNOT_DECODE =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 792;



  /**
   * The message ID for the message that will be used if the simple paged
   * results control is not found in the response from the server.  This does
   * not take any arguments.
   */
  public static final int MSGID_PAGED_RESULTS_RESPONSE_NOT_FOUND =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 793;



  /**
   * The message ID for the message that will be used as the description of the
   * singleValueChanges argument.  This does not take any arguments.
   */
  public static final int MSGID_LDIFDIFF_DESCRIPTION_SINGLE_VALUE_CHANGES =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 794;


  /**
   * The message ID for the message that will be used if the prompt trust
   * manager is asked about trusting a client certificate.  This does not take
   * any arguments.
   */
  public static final int MSGID_PROMPTTM_REJECTING_CLIENT_CERT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 795;



  /**
   * The message ID for the message that will be used if the server did not
   * present a certificate chain.  This does not take any arguments.
   */
  public static final int MSGID_PROMPTTM_NO_SERVER_CERT_CHAIN =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 796;



  /**
   * The message ID for the message that will be used if the server certificate
   * is expired.  This takes a single argument, which is a string representation
   * of the "notAfter" date.
   */
  public static final int MSGID_PROMPTTM_CERT_EXPIRED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 797;



  /**
   * The message ID for the message that will be used if the server certificate
   * is not yet valid.  This takes a single argument, which is a string
   * representation of the "notBefore" date.
   */
  public static final int MSGID_PROMPTTM_CERT_NOT_YET_VALID =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_WARNING | 798;



  /**
   * The message ID for the message that will be used to provide details about
   * the server certificate.  This takes four arguments, which are the string
   * representations of the certificate's subject DN, issuer DN, validity start
   * date, and validity end date.
   */
  public static final int MSGID_PROMPTTM_SERVER_CERT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 799;



  /**
   * The message ID for the message that will be used to prompt the user to
   * enter "yes" or "no".  This does not take any arguments.
   */
  public static final int MSGID_PROMPTTM_YESNO_PROMPT =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 800;



  /**
   * The message ID for the message that will be used if the user rejected the
   * certificate presented by the server.  This does not take any arguments.
   */
  public static final int MSGID_PROMPTTM_USER_REJECTED =
       CATEGORY_MASK_TOOLS | SEVERITY_MASK_SEVERE_ERROR | 801;


  /**
   * The message ID for the message that will be used when the server is
   * already stopped.  This does not take any arguments.
   */
  public static final int MSGID_STOPDS_SERVER_ALREADY_STOPPED =
      CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 802;

  /**
   * The message ID for the message that will be used when the server is
   * going to stopped.  This does not take any arguments.
   */
  public static final int MSGID_STOPDS_GOING_TO_STOP =
      CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 803;

  /**
   * The message ID for the message that will be used as the description for the
   * checkStoppability command-line argument.  This does not take any arguments.
   */
  public static final int MSGID_STOPDS_CHECK_STOPPABILITY =
      CATEGORY_MASK_TOOLS | SEVERITY_MASK_INFORMATIONAL | 804;

  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_TOOLS_CANNOT_CREATE_SSL_CONNECTION,
                    "Unable to create an SSL connection to the server: %s");
    registerMessage(MSGID_TOOLS_SSL_CONNECTION_NOT_INITIALIZED,
                    "Unable to create an SSL connection to the server because" +
                    " the connection factory has not been initialized.");
    registerMessage(MSGID_TOOLS_CANNOT_LOAD_KEYSTORE_FILE,
                    "Cannot load the key store file: %s.");
    registerMessage(MSGID_TOOLS_CANNOT_INIT_KEYMANAGER,
                    "Cannot initialize the key manager for the key store:" +
                    "%s.");
    registerMessage(MSGID_TOOLS_CANNOT_LOAD_TRUSTSTORE_FILE,
                    "Cannot load the key store file: %s.");
    registerMessage(MSGID_TOOLS_CANNOT_INIT_TRUSTMANAGER,
                    "Cannot initialize the key manager for the key store:" +
                    "%s.");


    registerMessage(MSGID_ENCPW_DESCRIPTION_LISTSCHEMES,
                    "List available password storage schemes");
    registerMessage(MSGID_ENCPW_DESCRIPTION_CLEAR_PW,
                    "Clear-text password to encode or to compare against an " +
                    "encoded password");
    registerMessage(MSGID_ENCPW_DESCRIPTION_CLEAR_PW_FILE,
                    "Clear-text password file");
    registerMessage(MSGID_ENCPW_DESCRIPTION_ENCODED_PW,
                    "Encoded password to compare against the clear-text " +
                    "password");
    registerMessage(MSGID_ENCPW_DESCRIPTION_ENCODED_PW_FILE,
                    "Encoded password file");
    registerMessage(MSGID_ENCPW_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that serves as the configuration handler for the " +
                    "Directory Server.");
    registerMessage(MSGID_ENCPW_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the Directory Server " +
                    "configuration file.");
    registerMessage(MSGID_ENCPW_DESCRIPTION_SCHEME,
                    "Scheme to use for the encoded password");
    registerMessage(MSGID_ENCPW_DESCRIPTION_AUTHPW,
                    "Use the authentication password syntax rather than the " +
                    "user password syntax");
    registerMessage(MSGID_ENCPW_DESCRIPTION_USE_COMPARE_RESULT,
                    "Use the LDAP compare result as an exit code for the " +
                    "password comparison");
    registerMessage(MSGID_ENCPW_DESCRIPTION_USAGE,
                    "Displays this usage information.");
    registerMessage(MSGID_ENCPW_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_ENCPW_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_ENCPW_NO_CLEAR_PW,
                    "No clear-text password was specified.  Use --%s or --%s " +
                    "to specify the password to encode.");
    registerMessage(MSGID_ENCPW_NO_SCHEME,
                    "No password storage scheme was specified.  Use the --%s " +
                    "argument to specify the storage scheme.");
    registerMessage(MSGID_ENCPW_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_ENCPW_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_ENCPW_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_ENCPW_CANNOT_INITIALIZE_CORE_CONFIG,
                    "An error occurred while trying to initialize the core " +
                    "Directory Server configuration:  %s.");
    registerMessage(MSGID_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES,
                    "An error occurred while trying to initialize the " +
                    "Directory Server password storage schemes:  %s.");
    registerMessage(MSGID_ENCPW_NO_AUTH_STORAGE_SCHEMES,
                    "No authentication password storage schemes have been " +
                    "configured for use in the Directory Server.");
    registerMessage(MSGID_ENCPW_NO_STORAGE_SCHEMES,
                    "No password storage schemes have been configured for " +
                    "use in the Directory Server.");
    registerMessage(MSGID_ENCPW_NO_SUCH_AUTH_SCHEME,
                    "Authentication password storage scheme \"%s\" is not "+
                    "configured for use in the Directory Server.");
    registerMessage(MSGID_ENCPW_NO_SUCH_SCHEME,
                    "Password storage scheme \"%s\" is not configured for " +
                    "use in the Directory Server.");
    registerMessage(MSGID_ENCPW_INVALID_ENCODED_AUTHPW,
                    "The provided password is not a valid encoded " +
                    "authentication password value:  %s.");
    registerMessage(MSGID_ENCPW_PASSWORDS_MATCH,
                    "The provided clear-text and encoded passwords match.");
    registerMessage(MSGID_ENCPW_PASSWORDS_DO_NOT_MATCH,
                    "The provided clear-text and encoded passwords do not " +
                    "match.");
    registerMessage(MSGID_ENCPW_INVALID_ENCODED_USERPW,
                    "The provided password is not a valid encoded " +
                    "user password value:  %s.");
    registerMessage(MSGID_ENCPW_ENCODED_PASSWORD,
                    "Encoded Password:  \"%s\".");
    registerMessage(MSGID_ENCPW_CANNOT_ENCODE,
                    "An error occurred while attempting to encode the " +
                    "clear-text password:  %s.");


    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that serves as the configuration handler for the " +
                    "Directory Server.");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the Directory Server " +
                    "configuration file.");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_LDIF_FILE,
                    "Path to the LDIF file to be written");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_APPEND_TO_LDIF,
                    "Append an existing LDIF file rather than overwriting it");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_BACKEND_ID,
                    "Backend ID for the backend to export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_BRANCH,
                    "Base DN of a branch to include in the LDIF export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_BRANCH,
                    "Base DN of a branch to exclude from the LDIF export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_ATTRIBUTE,
                    "Attribute to include in the LDIF export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE,
                    "Attribute to exclude from the LDIF export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_INCLUDE_FILTER,
                    "Filter to identify entries to include in the LDIF export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_FILTER,
                    "Filter to identify entries to exclude from the LDIF " +
                    "export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_EXCLUDE_OPERATIONAL,
                    "Exclude operational attributes from the LDIF export");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_WRAP_COLUMN,
                    "Column at which to wrap long lines (0 for no wrapping)");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_COMPRESS_LDIF,
                    "Compress the LDIF data as it is exported");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_ENCRYPT_LDIF,
                    "Encrypt the LDIF data as it is exported");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_SIGN_HASH,
                    "Generate a signed hash of the export data");
    registerMessage(MSGID_LDIFEXPORT_DESCRIPTION_USAGE,
                    "Display this usage information");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LDIFEXPORT_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LDIFEXPORT_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CORE_CONFIG,
                    "An error occurred while trying to initialize the core " +
                    "Directory Server configuration:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER,
                    "An error occurred while attempting to initialize the " +
                    "crypto manager:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_INITIALIZE_PLUGINS,
                    "An error occurred while attempting to initialize the " +
                    "LDIF export plugins:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_PARSE_EXCLUDE_FILTER,
                    "Unable to decode exclude filter string \"%s\" as a " +
                    "valid search filter:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_PARSE_INCLUDE_FILTER,
                    "Unable to decode include filter string \"%s\" as a " +
                    "valid search filter:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DECODE_BASE_DN,
                    "Unable to decode base DN string \"%s\" as a valid " +
                    "distinguished name:  %s.");
    registerMessage(MSGID_LDIFEXPORT_MULTIPLE_BACKENDS_FOR_ID,
                    "Multiple Directory Server backends are configured with " +
                    "the requested backend ID \"%s\".");
    registerMessage(MSGID_LDIFEXPORT_NO_BACKENDS_FOR_ID,
                    "None of the Directory Server backends are configured " +
                    "with the requested backend ID \"%s\".");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_EXPORT_BACKEND,
                    "The Directory Server backend with backend ID \"%s\" " +
                    "does not provide a mechanism for performing LDIF " +
                    "exports.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DECODE_EXCLUDE_BASE,
                    "Unable to decode exclude branch string \"%s\" as a " +
                    "valid distinguished name:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DECODE_WRAP_COLUMN_AS_INTEGER,
                    "Unable to decode wrap column value \"%s\" as an " +
                    "integer.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_LOCK_BACKEND,
                    "An error occurred while attempting to acquire a shared " +
                    "lock for backend %s:  %s.  This generally means that " +
                    "some other process has an exclusive lock on this " +
                    "backend (e.g., an LDIF import or a restore).  The LDIF " +
                    "export cannot continue.");
    registerMessage(MSGID_LDIFEXPORT_ERROR_DURING_EXPORT,
                    "An error occurred while attempting to process the LDIF " +
                    "export:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_UNLOCK_BACKEND,
                    "An error occurred while attempting to release the " +
                    "shared lock for backend %s:  %s.  This lock should " +
                    "automatically be cleared when the export process exits, " +
                    "so no further action should be required.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DECODE_BACKEND_BASE_DN,
                    "Unable to decode the backend configuration base DN " +
                    "string \"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY,
                    "Unable to retrieve the backend configuration base entry " +
                    "\"%s\" from the server configuration:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_CLASS,
                    "Cannot determine the name of the Java class providing " +
                    "the logic for the backend defined in configuration " +
                    "entry %s:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_LOAD_BACKEND_CLASS,
                    "Unable to load class %s referenced in configuration " +
                    "entry %s for use as a Directory Server backend:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_INSTANTIATE_BACKEND_CLASS,
                    "Unable to create an instance of class %s referenced in " +
                    "configuration entry %s as a Directory Server backend:  " +
                    "%s.");
    registerMessage(MSGID_LDIFEXPORT_NO_BASES_FOR_BACKEND,
                    "No base DNs have been defined in backend configuration " +
                    "entry %s.  This backend will not be evaluated in the " +
                    "LDIF export process.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DETERMINE_BASES_FOR_BACKEND,
                    "Unable to determine the set of base DNs defined in " +
                    "backend configuration entry %s:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DETERMINE_BACKEND_ID,
                    "Cannot determine the backend ID for the backend defined " +
                    "in configuration entry %s:  %s.");
    registerMessage(MSGID_LDIFEXPORT_CANNOT_DECODE_INCLUDE_BASE,
                    "Unable to decode include branch string \"%s\" as a " +
                    "valid distinguished name:  %s.");
    registerMessage(MSGID_LDIFEXPORT_INVALID_INCLUDE_BASE,
                    "Provided include base DN \"%s\" is not handled by the " +
                    "backend with backend ID %s.");


    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that serves as the configuration handler for the " +
                    "Directory Server.");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the Directory Server " +
                    "configuration file.");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_LDIF_FILE,
                    "Path to the LDIF file to be imported");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_TEMPLATE_FILE,
                    "Path to a MakeLDIF template to use to generate the " +
                    "import data");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_APPEND,
                    "Append to an existing database rather than overwriting " +
                    "it");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_REPLACE_EXISTING,
                    "Replace existing entries when appending to the database");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_BACKEND_ID,
                    "Backend ID for the backend to import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_INCLUDE_BRANCH,
                    "Base DN of a branch to include in the LDIF import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_EXCLUDE_BRANCH,
                    "Base DN of a branch to exclude from the LDIF import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_INCLUDE_ATTRIBUTE,
                    "Attribute to include in the LDIF import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_EXCLUDE_ATTRIBUTE,
                    "Attribute to exclude from the LDIF import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_INCLUDE_FILTER,
                    "Filter to identify entries to include in the LDIF import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_EXCLUDE_FILTER,
                    "Filter to identify entries to exclude from the LDIF " +
                    "import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_REJECT_FILE,
                    "Write rejected entries to the specified file");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_OVERWRITE_REJECTS,
                    "Overwrite an existing rejects file rather than " +
                    "appending to it");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_RANDOM_SEED,
                    "Seed for the MakeLDIF random number generator");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_SKIP_SCHEMA_VALIDATION,
                    "Skip schema validation during the LDIF import");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_IS_COMPRESSED,
                    "LDIF file is compressed");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_IS_ENCRYPTED,
                    "LDIF file is encrypted");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_QUIET,
                    "Use quiet mode (no output)");
    registerMessage(MSGID_LDIFIMPORT_DESCRIPTION_USAGE,
                    "Display this usage information.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LDIFIMPORT_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CONFLICTING_OPTIONS,
                    "The %s and %s arguments are incompatible and may not be " +
                    "used together.");
    registerMessage(MSGID_LDIFIMPORT_MISSING_REQUIRED_ARGUMENT,
                    "Neither the %s or the %s argument was provided.  One " +
                    "of these arguments must be given to specify the source " +
                    "for the LDIF data to be imported.");
    registerMessage(MSGID_LDIFIMPORT_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CORE_CONFIG,
                    "An error occurred while trying to initialize the core " +
                    "Directory Server configuration:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_INITIALIZE_CRYPTO_MANAGER,
                    "An error occurred while attempting to initialize the " +
                    "crypto manager:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_INITIALIZE_PWPOLICY,
                    "An error occurred while attempting to initialize the " +
                    "password policy components:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_INITIALIZE_PLUGINS,
                    "An error occurred while attempting to initialize the " +
                    "LDIF import plugins:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_PARSE_EXCLUDE_FILTER,
                    "Unable to decode exclude filter string \"%s\" as a " +
                    "valid search filter:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_PARSE_INCLUDE_FILTER,
                    "Unable to decode include filter string \"%s\" as a " +
                    "valid search filter:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DECODE_BASE_DN,
                    "Unable to decode base DN string \"%s\" as a valid " +
                    "distinguished name:  %s.");
    registerMessage(MSGID_LDIFIMPORT_MULTIPLE_BACKENDS_FOR_ID,
                    "Multiple Directory Server backends are configured with " +
                    "backend ID \"%s\".");
    registerMessage(MSGID_LDIFIMPORT_NO_BACKENDS_FOR_ID,
                    "None of the Directory Server backends are configured " +
                    "with the requested backend ID \"%s\".");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_IMPORT,
                    "The Directory Server backend with backend ID %s does " +
                    "not provide a mechanism for performing LDIF imports.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DECODE_EXCLUDE_BASE,
                    "Unable to decode exclude branch string \"%s\" as a " +
                    "valid distinguished name:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DECODE_INCLUDE_BASE,
                    "Unable to decode include branch string \"%s\" as a " +
                    "valid distinguished name:  %s.");
    registerMessage(MSGID_LDIFIMPORT_INVALID_INCLUDE_BASE,
                    "Provided include base DN \"%s\" is not handled by the " +
                    "backend with backend ID %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_PARSE_TEMPLATE_FILE,
                    "Unable to parse the specified file %s as a MakeLDIF " +
                    "template file:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_OPEN_REJECTS_FILE,
                    "An error occurred while trying to open the rejects " +
                    "file %s for writing:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_LOCK_BACKEND,
                    "An error occurred while attempting to acquire an " +
                    "exclusive lock for backend %s:  %s.  This generally " +
                    "means some other process is still using this backend " +
                    "(e.g., it is in use by the Directory Server or a " +
                    "backup or LDIF export is in progress.  The LDIF import " +
                    "cannot continue.");
    registerMessage(MSGID_LDIFIMPORT_ERROR_DURING_IMPORT,
                    "An error occurred while attempting to process the LDIF " +
                    "import:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_UNLOCK_BACKEND,
                    "An error occurred while attempting to release the " +
                    "exclusive lock for backend %s:  %s.  This lock should " +
                    "automatically be cleared when the import process exits, " +
                    "so no further action should be required.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DECODE_BACKEND_BASE_DN,
                    "Unable to decode the backend configuration base DN " +
                    "string \"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY,
                    "Unable to retrieve the backend configuration base entry " +
                    "\"%s\" from the server configuration:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_ID,
                    "Cannot determine the backend ID for the backend defined " +
                    "in configuration entry %s:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_CLASS,
                    "Cannot determine the name of the Java class providing " +
                    "the logic for the backend defined in configuration " +
                    "entry %s:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_LOAD_BACKEND_CLASS,
                    "Unable to load class %s referenced in configuration " +
                    "entry %s for use as a Directory Server backend:  %s.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_INSTANTIATE_BACKEND_CLASS,
                    "Unable to create an instance of class %s referenced in " +
                    "configuration entry %s as a Directory Server backend:  " +
                    "%s.");
    registerMessage(MSGID_LDIFIMPORT_NO_BASES_FOR_BACKEND,
                    "No base DNs have been defined in backend configuration " +
                    "entry %s.  This backend will not be evaluated in the " +
                    "LDIF import process.");
    registerMessage(MSGID_LDIFIMPORT_CANNOT_DETERMINE_BASES_FOR_BACKEND,
                    "Unable to determine the set of base DNs defined in " +
                    "backend configuration entry %s:  %s.");


    registerMessage(MSGID_PROCESSING_OPERATION,
                    "Processing %s request for %s.");
    registerMessage(MSGID_OPERATION_FAILED,
                    "%s operation failed for DN %s. %s.");
    registerMessage(MSGID_OPERATION_SUCCESSFUL,
                    "%s operation successful for DN %s.");
    registerMessage(MSGID_PROCESSING_COMPARE_OPERATION,
                    "Comparing type %s with value %s in entry %s.");
    registerMessage(MSGID_COMPARE_OPERATION_RESULT_FALSE,
                    "Compare operation returned false for entry %s.");
    registerMessage(MSGID_COMPARE_OPERATION_RESULT_TRUE,
                    "Compare operation returned true for entry %s.");
    registerMessage(MSGID_SEARCH_OPERATION_INVALID_PROTOCOL,
                    "Invalid operation type returned in search result %s.");
    registerMessage(MSGID_DESCRIPTION_TRUSTALL,
                    "Trust all server SSL certificates");
    registerMessage(MSGID_DESCRIPTION_BINDDN,
                    "Bind DN");
    registerMessage(MSGID_DESCRIPTION_BINDPASSWORD,
                    "Bind password");
    registerMessage(MSGID_DESCRIPTION_BINDPASSWORDFILE,
                    "Bind password file");
    registerMessage(MSGID_DESCRIPTION_PROXY_AUTHZID,
                    "Use the proxied authorization control with the given " +
                    "authorization ID");
    registerMessage(MSGID_DESCRIPTION_PSEARCH_INFO,
                    "Use the persistent search control");
    registerMessage(MSGID_DESCRIPTION_SIMPLE_PAGE_SIZE,
                    "Use the simple paged results control with the given " +
                    "page size");
    registerMessage(MSGID_DESCRIPTION_REPORT_AUTHZID,
                    "Use the authorization identity control");
    registerMessage(MSGID_DESCRIPTION_USE_PWP_CONTROL,
                    "Use the password policy request control");
    registerMessage(MSGID_BIND_AUTHZID_RETURNED,
                    "# Bound with authorization ID %s.");
    registerMessage(MSGID_BIND_PASSWORD_EXPIRED,
                    "# Your password has expired.");
    registerMessage(MSGID_BIND_PASSWORD_EXPIRING,
                    "# Your password will expire in %s.");
    registerMessage(MSGID_BIND_ACCOUNT_LOCKED,
                    "# Your account has been locked.");
    registerMessage(MSGID_BIND_MUST_CHANGE_PASSWORD,
                    "# You must change your password before any other " +
                    "operations will be allowed.");
    registerMessage(MSGID_BIND_GRACE_LOGINS_REMAINING,
                    "# You have %d grace logins remaining.");
    registerMessage(MSGID_DESCRIPTION_VERBOSE,
                    "Use verbose mode");
    registerMessage(MSGID_DESCRIPTION_KEYSTOREPATH,
                    "Certificate keystore path");
    registerMessage(MSGID_DESCRIPTION_TRUSTSTOREPATH,
                    "Certificate trust store path");
    registerMessage(MSGID_DESCRIPTION_KEYSTOREPASSWORD,
                    "Certificate keystore PIN");
    registerMessage(MSGID_DESCRIPTION_KEYSTOREPASSWORD_FILE,
                    "Certificate keystore PIN file");
    registerMessage(MSGID_DESCRIPTION_TRUSTSTOREPASSWORD,
                    "Certificate trust store PIN");
    registerMessage(MSGID_DESCRIPTION_TRUSTSTOREPASSWORD_FILE,
                    "Certificate trust store PIN file");
    registerMessage(MSGID_DESCRIPTION_HOST,
                    "Directory server hostname or IP address");
    registerMessage(MSGID_DESCRIPTION_PORT,
                    "Directory server port number");
    registerMessage(MSGID_DESCRIPTION_VERSION,
                    "LDAP protocol version number");
    registerMessage(MSGID_DESCRIPTION_SHOWUSAGE,
                    "Display this usage information");
    registerMessage(MSGID_DESCRIPTION_CONTROLS,
                    "Use a request control with the provided information");
    registerMessage(MSGID_DESCRIPTION_CONTINUE_ON_ERROR,
                    "Continue processing even if there are errors");
    registerMessage(MSGID_DESCRIPTION_USE_SSL,
                    "Use SSL for secure communication with the server");
    registerMessage(MSGID_DESCRIPTION_START_TLS,
                    "Use StartTLS to secure communication with the server");
    registerMessage(MSGID_DESCRIPTION_USE_SASL_EXTERNAL,
                    "Use the SASL EXTERNAL authentication mechanism");
    registerMessage(MSGID_DESCRIPTION_ENCODING,
                    "Use the specified character set for command-line input");
    registerMessage(MSGID_DELETE_DESCRIPTION_FILENAME,
                    "File containing the DNs of the entries to delete");
    registerMessage(MSGID_SEARCH_DESCRIPTION_FILENAME,
                    "File containing a list of search filter strings");
    registerMessage(MSGID_COMPARE_DESCRIPTION_FILENAME,
                    "File containing the DNs of the entries to compare");
    registerMessage(MSGID_DELETE_DESCRIPTION_DELETE_SUBTREE,
                    "Delete the specified entry and all entries below it");
    registerMessage(MSGID_MODIFY_DESCRIPTION_DEFAULT_ADD,
                    "Treat records with no changetype as add operations");
    registerMessage(MSGID_DESCRIPTION_ASSERTION_FILTER,
                    "Use the LDAP assertion control with the provided filter");
    registerMessage(MSGID_DESCRIPTION_PREREAD_ATTRS,
                    "Use the LDAP ReadEntry pre-read control");
    registerMessage(MSGID_DESCRIPTION_POSTREAD_ATTRS,
                    "Use the LDAP ReadEntry post-read control");
    registerMessage(MSGID_DESCRIPTION_MATCHED_VALUES_FILTER,
                    "Use the LDAP matched values control with the provided " +
                    "filter");
    registerMessage(MSGID_COMPARE_CANNOT_BASE64_DECODE_ASSERTION_VALUE,
                    "The assertion value was indicated to be base64-encoded, " +
                    "but an error occurred while trying to decode the value.");
    registerMessage(MSGID_COMPARE_CANNOT_READ_ASSERTION_VALUE_FROM_FILE,
                    "Unable to read the assertion value from the specified " +
                    "file:  %s.");
    registerMessage(MSGID_SEARCH_DESCRIPTION_BASEDN,
                    "Search base DN");
    registerMessage(MSGID_SEARCH_DESCRIPTION_SIZE_LIMIT,
                    "Maximum number of entries to return from the search");
    registerMessage(MSGID_SEARCH_DESCRIPTION_TIME_LIMIT,
                    "Maximum length of time in seconds to allow for the " +
                    "search");
    registerMessage(MSGID_SEARCH_DESCRIPTION_SEARCH_SCOPE,
                    "Search scope ('base', 'one', 'sub', or 'subordinate')");
    registerMessage(MSGID_SEARCH_DESCRIPTION_DEREFERENCE_POLICY,
                    "Alias dereference policy ('never', 'always', 'search', " +
                    "or 'find')");


    registerMessage(MSGID_LDAPAUTH_CANNOT_SEND_SIMPLE_BIND,
                    "Cannot send the simple bind request:  %s.");
    registerMessage(MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE,
                    "The connection to the Directory Server was closed " +
                    "before the bind response could be read.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE,
                    "Cannot read the bind response from the server:  " +
                    "%s.");
    registerMessage(MSGID_LDAPAUTH_SERVER_DISCONNECT,
                    "The Directory Server indicated that it was closing the " +
                    "connection to the client (result code %d, message " +
                    "\"%s\".");
    registerMessage(MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE,
                    "The Directory Server sent an unexpected extended " +
                    "response message to the client:  %s.");
    registerMessage(MSGID_LDAPAUTH_UNEXPECTED_RESPONSE,
                    "The Directory Server sent an unexpected response " +
                    "message to the client:  %s.");
    registerMessage(MSGID_LDAPAUTH_SIMPLE_BIND_FAILED,
                    "The simple bind attempt failed:  result code %d (%s), " +
                    "error message \"%s\".");
    registerMessage(MSGID_LDAPAUTH_NO_SASL_MECHANISM,
                    "A SASL bind was requested but no SASL mechanism was " +
                    "specified.");
    registerMessage(MSGID_LDAPAUTH_UNSUPPORTED_SASL_MECHANISM,
                    "The requested SASL mechanism \"%s\" is not supported " +
                    "by this client.");
    registerMessage(MSGID_LDAPAUTH_TRACE_SINGLE_VALUED,
                    "The " + SASL_PROPERTY_TRACE + " SASL property may only " +
                    "be given a single value.");
    registerMessage(MSGID_LDAPAUTH_INVALID_SASL_PROPERTY,
                    "Property \"%s\" is not allowed for the %s SASL " +
                    "mechanism.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND,
                    "Cannot send the SASL %S bind request:  %s.");
    registerMessage(MSGID_LDAPAUTH_SASL_BIND_FAILED,
                    "The SASL %s bind attempt failed:  result code %d (%s), " +
                    "error message \"%s\".");
    registerMessage(MSGID_LDAPAUTH_NO_SASL_PROPERTIES,
                    "No SASL properties were provided for use with the %s " +
                    "mechanism.");
    registerMessage(MSGID_LDAPAUTH_AUTHID_SINGLE_VALUED,
                    "The \"" + SASL_PROPERTY_AUTHID + "\" SASL property only " +
                    "accepts a single value.");
    registerMessage(MSGID_LDAPAUTH_SASL_AUTHID_REQUIRED,
                    "The \"" + SASL_PROPERTY_AUTHID + "\" SASL property is " +
                    "required for use with the %s mechanism.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_SEND_INITIAL_SASL_BIND,
                    "Cannot send the initial bind request in the multi-stage " +
                    "%s bind to the server:  %s.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE,
                    "Cannot read the initial %s bind response from the " +
                    "server:  %s.");
    registerMessage(MSGID_LDAPAUTH_UNEXPECTED_INITIAL_BIND_RESPONSE,
                    "The client received an unexpected intermediate bind " +
                    "response.  The \"SASL bind in progress\" result was " +
                    "expected for the first response in the multi-stage %s " +
                    "bind process, but the bind response had a result code " +
                    "of %d (%s) and an error message of \"%s\".");
    registerMessage(MSGID_LDAPAUTH_NO_CRAMMD5_SERVER_CREDENTIALS,
                    "The initial bind response from the server did not " +
                    "include any server SASL credentials containing the " +
                    "challenge information needed to complete the CRAM-MD5 " +
                    "authentication.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_INITIALIZE_MD5_DIGEST,
                    "An unexpected error occurred while trying to initialize " +
                    "the MD5 digest generator:  %s.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_SEND_SECOND_SASL_BIND,
                    "Cannot send the second bind request in the multi-stage " +
                    "%s bind to the server:  %s.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE,
                    "Cannot read the second %s bind response from the " +
                    "server:  %s.");
    registerMessage(MSGID_LDAPAUTH_NO_ALLOWED_SASL_PROPERTIES,
                    "One or more SASL properties were provided, but the %s " +
                    "mechanism does not take any SASL properties.");
    registerMessage(MSGID_LDAPAUTH_AUTHZID_SINGLE_VALUED,
                    "The \"" + SASL_PROPERTY_AUTHZID + "\" SASL property " +
                    "only accepts a single value.");
    registerMessage(MSGID_LDAPAUTH_REALM_SINGLE_VALUED,
                    "The \"" + SASL_PROPERTY_REALM + "\" SASL property only " +
                    "accepts a single value.");
    registerMessage(MSGID_LDAPAUTH_QOP_SINGLE_VALUED,
                    "The \"" + SASL_PROPERTY_QOP + "\" SASL property only " +
                    "accepts a single value.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_QOP_NOT_SUPPORTED,
                    "The \"%s\" QoP mode is not supported by this client.  " +
                    "Only the \"auth\" mode is currently available for use.");
    // FIXME -- Update this message when auth-int and auth-conf are supported.
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_INVALID_QOP,
                    "The specified DIGEST-MD5 quality of protection mode " +
                    "\"%s\" is not valid.  The only QoP mode currently " +
                    "supported is \"auth\".");
    registerMessage(MSGID_LDAPAUTH_DIGEST_URI_SINGLE_VALUED,
                    "The \"" + SASL_PROPERTY_DIGEST_URI + "\" SASL property " +
                    "only accepts a single value.");
    registerMessage(MSGID_LDAPAUTH_NO_DIGESTMD5_SERVER_CREDENTIALS,
                    "The initial bind response from the server did not " +
                    "include any server SASL credentials containing the " +
                    "challenge information needed to complete the " +
                    "DIGEST-MD5 authentication.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_INVALID_TOKEN_IN_CREDENTIALS,
                    "The DIGEST-MD5 credentials provided by the server " +
                    "contained an invalid token of \"%s\" starting at " +
                    "position %d.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_INVALID_CHARSET,
                    "The DIGEST-MD5 credentials provided by the server " +
                    "specified the use of the \"%s\" character set.  The " +
                    "character set that may be specified in the DIGEST-MD5 " +
                    "credentials is \"utf-8\".");
    registerMessage(MSGID_LDAPAUTH_REQUESTED_QOP_NOT_SUPPORTED_BY_SERVER,
                    "The requested QoP mode of \"%s\" is not listed as " +
                    "supported by the Directory Server.  The Directory " +
                    "Server's list of supported QoP modes is:  \"%s\".");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_NO_NONCE,
                    "The server SASL credentials provided in response to the " +
                    "initial DIGEST-MD5 bind request did not include the " +
                    "nonce to use to generate the authentication digests.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_CANNOT_CREATE_RESPONSE_DIGEST,
                    "An error occurred while attempting to generate the " +
                    "response digest for the DIGEST-MD5 bind request:  %s.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_NO_RSPAUTH_CREDS,
                    "The DIGEST-MD5 bind response from the server did not " +
                    "include the \"rspauth\" element to provide a digest of " +
                    "the response authentication information.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_COULD_NOT_DECODE_RSPAUTH,
                    "An error occurred while trying to decode the rspauth " +
                    "element of the DIGEST-MD5 bind response from the server " +
                    "as a hexadecimal string:  %s.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_COULD_NOT_CALCULATE_RSPAUTH,
                    "An error occurred while trying to calculate the " +
                    "expected rspauth element to compare against the value " +
                    "included in the DIGEST-MD5 response from the server:  " +
                    "%s.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_RSPAUTH_MISMATCH,
                    "The rpsauth element included in the DIGEST-MD5 bind " +
                    "response from the Directory Server was different from " +
                    "the expected value calculated by the client.");
    registerMessage(MSGID_LDAPAUTH_DIGESTMD5_INVALID_CLOSING_QUOTE_POS,
                    "The DIGEST-MD5 response challenge could not be parsed " +
                    "because it had an invalid quotation mark at position %d.");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_TRACE,
                    "Specifies a text string that may be written to the " +
                    "Directory Server error log as trace information for " +
                    "the bind.");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHID,
                    "Specifies the authentication ID for the bind.");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_REALM,
                    "Specifies the realm into which the authentication is to " +
                    "be performed.");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_QOP,
                    "Specifies the quality of protection to use for the bind.");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_DIGEST_URI,
                    "Specifies the digest URI to use for the bind.");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHZID,
                    "Specifies the authorization ID to use for the bind.");
    registerMessage(MSGID_DESCRIPTION_SASL_PROPERTIES,
                    "SASL bind options");
    registerMessage(MSGID_DESCRIPTION_DONT_WRAP,
                    "Do not wrap long lines");
    registerMessage(MSGID_DESCRIPTION_COUNT_ENTRIES,
                    "Count the number of entries returned by the server");
    registerMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_KDC,
                    "Specifies the KDC to use for the Kerberos " +
                    "authentication.");
    registerMessage(MSGID_LDAPAUTH_KDC_SINGLE_VALUED,
                    "The \"" + SASL_PROPERTY_KDC + "\" SASL property only " +
                    "accepts a single value.");
    // FIXME -- Update this message when auth-int and auth-conf are supported.
    registerMessage(MSGID_LDAPAUTH_GSSAPI_INVALID_QOP,
                    "The specified GSSAPI quality of protection mode \"%s\" " +
                    "is not valid.  The only QoP mode currently supported is " +
                    "\"auth\".");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_JAAS_CONFIG,
                    "An error occurred while trying to create the " +
                    "temporary JAAS configuration for GSSAPI " +
                    "authentication:  %s.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_LOCAL_AUTHENTICATION_FAILED,
                    "An error occurred while attempting to perform local " +
                    "authentication to the Kerberos realm:  %s.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_REMOTE_AUTHENTICATION_FAILED,
                    "An error occurred while attempting to perform GSSAPI " +
                    "authentication to the Directory Server:  %s.");
    registerMessage(MSGID_LDAPAUTH_NONSASL_RUN_INVOCATION,
                    "The LDAPAuthenticationHandler.run() method was called " +
                    "for a non-SASL bind.  The backtrace for this call is %s.");
    registerMessage(MSGID_LDAPAUTH_UNEXPECTED_RUN_INVOCATION,
                    "The LDAPAuthenticationHandler.run() method was called " +
                    "for a SASL bind with an unexpected mechanism of " +
                    "\"%s\".  The backtrace for this call is %s.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_SASL_CLIENT,
                    "An error occurred while attempting to create a SASL " +
                    "client to process the GSSAPI authentication:  %s.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_INITIAL_CHALLENGE,
                    "An error occurred while attempting to create the " +
                    "initial challenge for GSSAPI authentication:  %s.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_CANNOT_VALIDATE_SERVER_CREDS,
                    "An error occurred while trying to validate the SASL " +
                    "credentials provided by the Directory Server in the " +
                    "GSSAPI bind response:  %s.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_UNEXPECTED_SUCCESS_RESPONSE,
                    "The Directory Server unexpectedly returned a success " +
                    "response to the client even though the client does not " +
                    "believe that the GSSAPI negotiation is complete.");
    registerMessage(MSGID_LDAPAUTH_GSSAPI_BIND_FAILED,
                    "The GSSAPI bind attempt failed.  The result code was %d " +
                    "(%s), and the error message was \"%s\".");
    registerMessage(MSGID_LDAPAUTH_NONSASL_CALLBACK_INVOCATION,
                    "The LDAPAuthenticationHandler.handle() method was " +
                    "called for a non-SASL bind.  The backtrace for this " +
                    "call is %s.");
    registerMessage(MSGID_LDAPAUTH_UNEXPECTED_GSSAPI_CALLBACK,
                    "The LDAPAuthenticationHandler.handle() method was " +
                    "called during a GSSAPI bind attempt with an unexpected " +
                    "callback type of %s.");
    registerMessage(MSGID_LDAPAUTH_UNEXPECTED_CALLBACK_INVOCATION,
                    "The LDAPAuthenticationHandler.handle() method was " +
                    "called for an unexpected SASL mechanism of %s.  The " +
                    "backtrace for this call is %s.");
    registerMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT,
                    "Password for user '%s':  ");
    registerMessage(MSGID_LDAPAUTH_CANNOT_SEND_WHOAMI_REQUEST,
                    "Cannot send the 'Who Am I?' request to the Directory " +
                    "Server:  %s.");
    registerMessage(MSGID_LDAPAUTH_CANNOT_READ_WHOAMI_RESPONSE,
                    "Cannot read the 'Who Am I?' response from the Directory " +
                    "Server:  %s.");
    registerMessage(MSGID_LDAPAUTH_WHOAMI_FAILED,
                    "The 'Who Am I?' request was rejected by the Directory " +
                    "Server with a result code of %d (%s) and and error " +
                    "message of \"%s\".");


    registerMessage(MSGID_DESCRIPTION_INVALID_VERSION,
                    "Invalid LDAP version number '%s'. Allowed values are " +
                    "2 and 3.");
    registerMessage(MSGID_SEARCH_INVALID_SEARCH_SCOPE,
                    "Invalid scope %s specified for the search request.");
    registerMessage(MSGID_SEARCH_NO_FILTERS,
                    "No filters specified for the search request.");
    registerMessage(MSGID_PAGED_RESULTS_REQUIRES_SINGLE_FILTER,
                    "The simple paged results control may only be used with " +
                    "a single search filter.");
    registerMessage(MSGID_PAGED_RESULTS_CANNOT_DECODE,
                    "Unable to decode the simple paged results control from " +
                    "the search response:  %s.");
    registerMessage(MSGID_PAGED_RESULTS_RESPONSE_NOT_FOUND,
                    "The simple paged results response control was not found " +
                    "in the search result done message from the server.");
    registerMessage(MSGID_PSEARCH_MISSING_DESCRIPTOR,
                    "The request to use the persistent search control did " +
                    "not include a descriptor that indicates the options to " +
                    "use with that control.");
    registerMessage(MSGID_PSEARCH_DOESNT_START_WITH_PS,
                    "The persistent search descriptor %s did not start with " +
                    "the required 'ps' string.");
    registerMessage(MSGID_PSEARCH_INVALID_CHANGE_TYPE,
                    "The provided change type value %s is invalid.  The " +
                    "recognized change types are add, delete, modify, " +
                    "modifydn, and any.");
    registerMessage(MSGID_PSEARCH_INVALID_CHANGESONLY,
                    "The provided changesOnly value %s is invalid.  Allowed " +
                    "values are 1 to only return matching entries that have " +
                    "changed since the beginning of the search, or 0 to also " +
                    "include existing entries that match the search criteria.");
    registerMessage(MSGID_PSEARCH_INVALID_RETURN_ECS,
                    "The provided returnECs value %s is invalid.  Allowed " +
                    "values are 1 to request that the entry change " +
                    "notification control be included in updated entries, or " +
                    "0 to exclude the control from matching entries.");
    registerMessage(MSGID_LDAP_ASSERTION_INVALID_FILTER,
                    "The search filter provided for the LDAP assertion " +
                    "control was invalid:  %s.");
    registerMessage(MSGID_LDAP_MATCHEDVALUES_INVALID_FILTER,
                    "The provided matched values filter was invalid:  %s.");
    registerMessage(MSGID_LDAPMODIFY_PREREAD_NO_VALUE,
                    "The pre-read response control did not include a value.");
    registerMessage(MSGID_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE,
                    "An error occurred while trying to decode the entry " +
                    "contained in the value of the pre-read response " +
                    "control:  %s.");
    registerMessage(MSGID_LDAPMODIFY_PREREAD_ENTRY,
                    "Target entry before the operation:");
    registerMessage(MSGID_LDAPMODIFY_POSTREAD_NO_VALUE,
                    "The post-read response control did not include a value.");
    registerMessage(MSGID_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE,
                    "An error occurred while trying to decode the entry " +
                    "contained in the value of the post-read response " +
                    "control:  %s.");
    registerMessage(MSGID_LDAPMODIFY_POSTREAD_ENTRY,
                    "Target entry after the operation:");



    registerMessage(MSGID_VERIFYINDEX_DESCRIPTION_BASE_DN,
                    "Specifies the base DN of a backend supporting indexing. " +
                    "Verification is performed on indexes within the scope " +
                    "of the given base DN.");
    registerMessage(MSGID_VERIFYINDEX_DESCRIPTION_INDEX_NAME,
                    "Specifies the name of an index to be verified. For an " +
                    "attribute index this is simply an attribute name.  " +
                    "Multiple indexes may be verified for completeness, or " +
                    "all indexes if no indexes are specified.  An index is " +
                    "complete if each index value references all entries " +
                    "containing that value.");
    registerMessage(MSGID_VERIFYINDEX_DESCRIPTION_VERIFY_CLEAN,
                    "Specifies that a single index should be verified to " +
                    "ensure it is clean.  An index is clean if each index " +
                    "value references only entries containing that value.  " +
                    "Only one index at a time may be verified in this way.");
    registerMessage(MSGID_VERIFYINDEX_ERROR_DURING_VERIFY,
                    "An error occurred while attempting to perform index " +
                    "verification:  %s.");
    registerMessage(MSGID_VERIFYINDEX_VERIFY_CLEAN_REQUIRES_SINGLE_INDEX,
                    "Only one index at a time may be verified for " +
                    "cleanliness.");
    registerMessage(MSGID_VERIFYINDEX_WRONG_BACKEND_TYPE,
                    "The backend does not support indexing.");
    registerMessage(MSGID_VERIFYINDEX_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that serves as the configuration handler for the " +
                    "Directory Server.");
    registerMessage(MSGID_VERIFYINDEX_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the Directory Server " +
                    "configuration file.");
    registerMessage(MSGID_VERIFYINDEX_DESCRIPTION_USAGE,
                    "Displays this usage information.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_VERIFYINDEX_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_VERIFYINDEX_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_INITIALIZE_CORE_CONFIG,
                    "An error occurred while trying to initialize the core " +
                    "Directory Server configuration:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_LOCK_BACKEND,
                    "An error occurred while attempting to acquire a shared " +
                    "lock for backend %s:  %s.  This generally means that " +
                    "some other process has an exclusive lock on this " +
                    "backend (e.g., an LDIF import or a restore).  The " +
                    "index verification cannot continue.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_UNLOCK_BACKEND,
                    "An error occurred while attempting to release the " +
                    "shared lock for backend %s:  %s.  This lock should " +
                    "automatically be cleared when the verification process " +
                    "exits, so no further action should be required.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_DECODE_BASE_DN,
                    "Unable to decode base DN string \"%s\" as a valid " +
                    "distinguished name:  %s.");
    registerMessage(MSGID_VERIFYINDEX_MULTIPLE_BACKENDS_FOR_BASE,
                    "Multiple Directory Server backends are configured to " +
                    "support base DN \"%s\".");
    registerMessage(MSGID_VERIFYINDEX_NO_BACKENDS_FOR_BASE,
                    "None of the Directory Server backends are configured " +
                    "to support the requested base DN \"%s\".");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_DECODE_BACKEND_BASE_DN,
                    "Unable to decode the backend configuration base DN " +
                    "string \"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY,
                    "Unable to retrieve the backend configuration base entry " +
                    "\"%s\" from the server configuration:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_DETERMINE_BACKEND_CLASS,
                    "Cannot determine the name of the Java class providing " +
                    "the logic for the backend defined in configuration " +
                    "entry %s:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_LOAD_BACKEND_CLASS,
                    "Unable to load class %s referenced in configuration " +
                    "entry %s for use as a Directory Server backend:  %s.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_INSTANTIATE_BACKEND_CLASS,
                    "Unable to create an instance of class %s referenced in " +
                    "configuration entry %s as a Directory Server backend:  " +
                    "%s.");
    registerMessage(MSGID_VERIFYINDEX_NO_BASES_FOR_BACKEND,
                    "No base DNs have been defined in backend configuration " +
                    "entry %s.  This backend will not be evaluated in the " +
                    "data verification process.");
    registerMessage(MSGID_VERIFYINDEX_CANNOT_DETERMINE_BASES_FOR_BACKEND,
                    "Unable to determine the set of base DNs defined in " +
                    "backend configuration entry %s:  %s.");


    registerMessage(MSGID_BACKUPDB_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that serves as the configuration handler for the " +
                    "Directory Server.");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the Directory Server " +
                    "configuration file.");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_BACKEND_ID,
                    "Backend ID for the backend to archive");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_BACKUP_ALL,
                    "Back up all backends in the server");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_BACKUP_ID,
                    "Use the provided identifier for the backup");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_BACKUP_DIR,
                    "Path to the target directory for the backup file(s)");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_INCREMENTAL,
                    "Perform an incremental backup rather than a full backup");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_INCREMENTAL_BASE_ID,
                    "Backup ID of the source archive for an incremental " +
                    "backup");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_COMPRESS,
                    "Compress the backup contents");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_ENCRYPT,
                    "Encrypt the backup contents");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_HASH,
                    "Generate a hash of the backup contents");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_SIGN_HASH,
                    "Sign the hash of the backup contents");
    registerMessage(MSGID_BACKUPDB_DESCRIPTION_USAGE,
                    "Display this usage information");
    registerMessage(MSGID_BACKUPDB_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_BACKUPDB_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_BACKUPDB_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_INITIALIZE_CORE_CONFIG,
                    "An error occurred while trying to initialize the core " +
                    "Directory Server configuration:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_INITIALIZE_CRYPTO_MANAGER,
                    "An error occurred while attempting to initialize the " +
                    "crypto manager:  %s.");
    registerMessage(MSGID_BACKUPDB_MULTIPLE_BACKENDS_FOR_ID,
                    "Multiple Directory Server backends are configured with " +
                    "the requested backend ID \"%s\".");
    registerMessage(MSGID_BACKUPDB_NO_BACKENDS_FOR_ID,
                    "None of the Directory Server backends are configured " +
                    "with the requested backend ID \"%s\".");
    registerMessage(MSGID_BACKUPDB_CONFIG_ENTRY_MISMATCH,
                    "The configuration for the backend with backend ID %s is " +
                    "held in entry \"%s\", but other backups in the target " +
                    "backup directory %s were generated from a backend whose " +
                    "configuration was held in configuration entry \"%s\".");
    registerMessage(MSGID_BACKUPDB_INVALID_BACKUP_DIR,
                    "An error occurred while attempting to use the specified " +
                    "path \"%s\" as the target directory for the backup:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_BACKUP,
                    "The target backend %s cannot be backed up using the " +
                    "requested configuration:  %s.");
    registerMessage(MSGID_BACKUPDB_ERROR_DURING_BACKUP,
                    "An error occurred while attempting to back up backend " +
                    "%s with the requested configuration:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_DECODE_BACKEND_BASE_DN,
                    "Unable to decode the backend configuration base DN " +
                    "string \"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY,
                    "Unable to retrieve the backend configuration base entry " +
                    "\"%s\" from the server configuration:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_CLASS,
                    "Cannot determine the name of the Java class providing " +
                    "the logic for the backend defined in configuration " +
                    "entry %s:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_LOAD_BACKEND_CLASS,
                    "Unable to load class %s referenced in configuration " +
                    "entry %s for use as a Directory Server backend:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_INSTANTIATE_BACKEND_CLASS,
                    "Unable to create an instance of class %s referenced in " +
                    "configuration entry %s as a Directory Server backend:  " +
                    "%s.");
    registerMessage(MSGID_BACKUPDB_NO_BASES_FOR_BACKEND,
                    "No base DNs have been defined in backend configuration " +
                    "entry %s.  This backend will not be evaluated in the " +
                    "LDIF export process.");
    registerMessage(MSGID_BACKUPDB_CANNOT_DETERMINE_BASES_FOR_BACKEND,
                    "Unable to determine the set of base DNs defined in " +
                    "backend configuration entry %s:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_DETERMINE_BACKEND_ID,
                    "Cannot determine the backend ID for the backend defined " +
                    "in configuration entry %s:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID,
                    "The %s and %s arguments may not be used together.  " +
                    "Exactly one of them must be provided.");
    registerMessage(MSGID_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID,
                    "Neither the %s argument nor the %s argument was " +
                    "provided.  Exactly one of them is required.");
    registerMessage(MSGID_BACKUPDB_CANNOT_CREATE_BACKUP_DIR,
                    "An error occurred while attempting to create the backup " +
                    "directory %s:  %s.");
    registerMessage(MSGID_BACKUPDB_BACKUP_NOT_SUPPORTED,
                    "Backend ID %s was included in the set of backends to " +
                    "archive, but this backend does not provide support for " +
                    "a backup mechanism.  It will be skipped.");
    registerMessage(MSGID_BACKUPDB_NO_BACKENDS_TO_ARCHIVE,
                    "None of the target backends provide a backup " +
                    "mechanism.  The backup operation has been aborted.");
    registerMessage(MSGID_BACKUPDB_CANNOT_LOCK_BACKEND,
                    "An error occurred while attempting to acquire a shared " +
                    "lock for backend %s:  %s.  This generally means that " +
                    "some other process has exclusive access to this " +
                    "backend (e.g., a restore or an LDIF import).  This " +
                    "backend will not be archived.");
    registerMessage(MSGID_BACKUPDB_STARTING_BACKUP,
                    "Starting backup for backend %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR,
                    "An error occurred while attempting to parse the backup " +
                    "descriptor file %s:  %s.");
    registerMessage(MSGID_BACKUPDB_CANNOT_UNLOCK_BACKEND,
                    "An error occurred while attempting to release the " +
                    "shared lock for backend %s:  %s.  This lock should " +
                    "automatically be cleared when the backup process exits, " +
                    "so no further action should be required.");
    registerMessage(MSGID_BACKUPDB_COMPLETED_WITH_ERRORS,
                    "The backup process completed with one or more errors.");
    registerMessage(MSGID_BACKUPDB_COMPLETED_SUCCESSFULLY,
                    "The backup process completed successfully.");
    registerMessage(MSGID_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL,
                    "The use of the %s argument requires that the %s " +
                    "argument is also provided.");
    registerMessage(MSGID_BACKUPDB_SIGN_REQUIRES_HASH,
                    "The use of the %s argument requires that the %s " +
                    "argument is also provided.");


    registerMessage(MSGID_RESTOREDB_DESCRIPTION_CONFIG_CLASS,
                    "Specifies the fully-qualified name of the Java class " +
                    "that serves as the configuration handler for the " +
                    "Directory Server.");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_CONFIG_FILE,
                    "Specifies the path to the Directory Server " +
                    "configuration file.");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_BACKEND_ID,
                    "Backend ID for the backend to restore");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_BACKUP_ID,
                    "Backup ID of the backup to restore");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_BACKUP_DIR,
                    "Path to the directory containing the backup file(s)");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_LIST_BACKUPS,
                    "List available backups in the backup directory");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_VERIFY_ONLY,
                    "Verify the contents of the backup but do not restore it");
    registerMessage(MSGID_RESTOREDB_DESCRIPTION_USAGE,
                    "Display this usage information");
    registerMessage(MSGID_RESTOREDB_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_RESTOREDB_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_RESTOREDB_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_INITIALIZE_CORE_CONFIG,
                    "An error occurred while trying to initialize the core " +
                    "Directory Server configuration:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_INITIALIZE_CRYPTO_MANAGER,
                    "An error occurred while attempting to initialize the " +
                    "crypto manager:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY,
                    "An error occurred while attempting to examine the " +
                    "set of backups contained in backup directory %s:  %s.");
    registerMessage(MSGID_RESTOREDB_LIST_BACKUP_ID,
                    "Backup ID:          %s");
    registerMessage(MSGID_RESTOREDB_LIST_BACKUP_DATE,
                    "Backup Date:        %s");
    registerMessage(MSGID_RESTOREDB_LIST_INCREMENTAL,
                    "Is Incremental:     %s");
    registerMessage(MSGID_RESTOREDB_LIST_COMPRESSED,
                    "Is Compressed:      %s");
    registerMessage(MSGID_RESTOREDB_LIST_ENCRYPTED,
                    "Is Encrypted:       %s");
    registerMessage(MSGID_RESTOREDB_LIST_HASHED,
                    "Has Unsigned Hash:  %s");
    registerMessage(MSGID_RESTOREDB_LIST_SIGNED,
                    "Has Signed Hash:    %s");
    registerMessage(MSGID_RESTOREDB_LIST_DEPENDENCIES,
                    "Dependent Upon:     %s");
    registerMessage(MSGID_RESTOREDB_INVALID_BACKUP_ID,
                    "The requested backup ID %s does not exist in %s.");
    registerMessage(MSGID_RESTOREDB_NO_BACKUPS_IN_DIRECTORY,
                    "There are no Directory Server backups contained in " +
                    "%s.");
    registerMessage(MSGID_RESTOREDB_NO_BACKENDS_FOR_DN,
                    "The backups contained in directory %s were taken from " +
                    "a Directory Server backend defined in configuration " +
                    "entry %s but no such backend is available.");
    registerMessage(MSGID_RESTOREDB_CANNOT_RESTORE,
                    "The Directory Server backend configured with backend ID " +
                    "%s does not provide a mechanism for restoring " +
                    "backups.");
    registerMessage(MSGID_RESTOREDB_CANNOT_LOCK_BACKEND,
                    "An error occurred while attempting to acquire an " +
                    "exclusive lock for backend %s:  %s.  This generally " +
                    "means some other process is still using this backend " +
                    "(e.g., it is in use by the Directory Server or a " +
                    "backup or LDIF export is in progress.  The restore " +
                    "cannot continue.");
    registerMessage(MSGID_RESTOREDB_ERROR_DURING_BACKUP,
                    "An unexpected error occurred while attempting to " +
                    "restore backup %s from %s:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_UNLOCK_BACKEND,
                    "An error occurred while attempting to release the " +
                    "exclusive lock for backend %s:  %s.  This lock should " +
                    "automatically be cleared when the restore process " +
                    "exits, so no further action should be required.");
    registerMessage(MSGID_RESTOREDB_CANNOT_DECODE_BACKEND_BASE_DN,
                    "Unable to decode the backend configuration base DN " +
                    "string \"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY,
                    "Unable to retrieve the backend configuration base entry " +
                    "\"%s\" from the server configuration:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_CLASS,
                    "Cannot determine the name of the Java class providing " +
                    "the logic for the backend defined in configuration " +
                    "entry %s:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_LOAD_BACKEND_CLASS,
                    "Unable to load class %s referenced in configuration " +
                    "entry %s for use as a Directory Server backend:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_INSTANTIATE_BACKEND_CLASS,
                    "Unable to create an instance of class %s referenced in " +
                    "configuration entry %s as a Directory Server backend:  " +
                    "%s.");
    registerMessage(MSGID_RESTOREDB_NO_BASES_FOR_BACKEND,
                    "No base DNs have been defined in backend configuration " +
                    "entry %s.  This backend will not be evaluated in the " +
                    "LDIF export process.");
    registerMessage(MSGID_RESTOREDB_CANNOT_DETERMINE_BASES_FOR_BACKEND,
                    "Unable to determine the set of base DNs defined in " +
                    "backend configuration entry %s:  %s.");
    registerMessage(MSGID_RESTOREDB_CANNOT_DETERMINE_BACKEND_ID,
                    "Cannot determine the backend ID for the backend defined " +
                    "in configuration entry %s:  %s.");
    registerMessage(MSGID_DESCRIPTION_NOOP,
                    "Show what would be done but do not perform any operation");
    registerMessage(MSGID_DESCRIPTION_TYPES_ONLY,
                    "Only retrieve attribute names but not their values");
    registerMessage(MSGID_LDIF_FILE_CANNOT_OPEN_FOR_READ,
                    "An error occurred while attempting to open the " +
                    "LDIF file %s for reading:  %s.");
    registerMessage(MSGID_LDIF_FILE_READ_ERROR,
                    "An error occurred while attempting to read the contents " +
                    "of LDIF file %s:  %s.");
    registerMessage(MSGID_LDIF_FILE_INVALID_LDIF_ENTRY,
                    "Error at or near line %d in LDIF file %s:  %s.");


    registerMessage(MSGID_STOPDS_DESCRIPTION_HOST,
                    "Directory server hostname or IP address");
    registerMessage(MSGID_STOPDS_DESCRIPTION_PORT,
                    "Directory server port number");
    registerMessage(MSGID_STOPDS_DESCRIPTION_USESSL,
                    "Use SSL for secure communication with the server");
    registerMessage(MSGID_STOPDS_DESCRIPTION_USESTARTTLS,
                    "Use StartTLS for secure communication with the server");
    registerMessage(MSGID_STOPDS_DESCRIPTION_BINDDN,
                    "Bind DN");
    registerMessage(MSGID_STOPDS_DESCRIPTION_BINDPW,
                    "Bind password");
    registerMessage(MSGID_STOPDS_DESCRIPTION_BINDPWFILE,
                    "Bind password file");
    registerMessage(MSGID_STOPDS_DESCRIPTION_SASLOPTIONS,
                    "SASL bind options");
    registerMessage(MSGID_STOPDS_DESCRIPTION_PROXYAUTHZID,
                    "Use the proxied authorization control with the given " +
                    "authorization ID");
    registerMessage(MSGID_STOPDS_DESCRIPTION_STOP_REASON,
                    "Reason the server is being stopped or restarted");
    registerMessage(MSGID_STOPDS_DESCRIPTION_RESTART,
                    "Attempt to automatically restart the server once it has " +
                    "stopped");
    registerMessage(MSGID_STOPDS_CHECK_STOPPABILITY,
                    "Used to determine whether the server is stopped or not.");
    registerMessage(MSGID_STOPDS_DESCRIPTION_STOP_TIME,
                    "Time to begin the shutdown in YYYYMMDDhhmmss format " +
                    "(local time)");
    registerMessage(MSGID_STOPDS_DESCRIPTION_TRUST_ALL,
                    "Trust all server SSL certificates");
    registerMessage(MSGID_STOPDS_DESCRIPTION_KSFILE,
                    "Certificate keystore path");
    registerMessage(MSGID_STOPDS_DESCRIPTION_KSPW,
                    "Certificate keystore PIN");
    registerMessage(MSGID_STOPDS_DESCRIPTION_KSPWFILE,
                    "Certificate keystore PIN file");
    registerMessage(MSGID_STOPDS_DESCRIPTION_TSFILE,
                    "Certificate trust store path");
    registerMessage(MSGID_STOPDS_DESCRIPTION_TSPW,
                    "Certificate trust store PIN");
    registerMessage(MSGID_STOPDS_DESCRIPTION_TSPWFILE,
                    "Certificate trust store PIN file");
    registerMessage(MSGID_STOPDS_DESCRIPTION_SHOWUSAGE,
                    "Display this usage information.");
    registerMessage(MSGID_STOPDS_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_STOPDS_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_STOPDS_MUTUALLY_EXCLUSIVE_ARGUMENTS,
                    "ERROR:  You may not provide both the %s and the %s " +
                    "arguments.");
    registerMessage(MSGID_STOPDS_CANNOT_DECODE_STOP_TIME,
                    "ERROR:  Unable to decode the provided stop time.  It " +
                    "should be in the form YYYYMMDDhhmmssZ for UTC time or " +
                    "YYYYMMDDhhmmss for local time.");
    registerMessage(MSGID_STOPDS_CANNOT_INITIALIZE_SSL,
                    "ERROR:  Unable to perform SSL initialization:  %s.");
    registerMessage(MSGID_STOPDS_CANNOT_PARSE_SASL_OPTION,
                    "ERROR:  The provided SASL option string \"%s\" could " +
                    "not be parsed in the form \"name=value\".");
    registerMessage(MSGID_STOPDS_NO_SASL_MECHANISM,
                    "ERROR:  One or more SASL options were provided, but " +
                    "none of them were the \"mech\" option to specify which " +
                    "SASL mechanism should be used.");
    registerMessage(MSGID_STOPDS_CANNOT_DETERMINE_PORT,
                    "ERROR:  Cannot parse the value of the %s argument as " +
                    "an integer value between 1 and 65535:  %s.");
    registerMessage(MSGID_STOPDS_CANNOT_CONNECT,
                    "ERROR:  Cannot establish a connection to the " +
                    "Directory Server:  %s.");
    registerMessage(MSGID_STOPDS_UNEXPECTED_CONNECTION_CLOSURE,
                    "NOTICE:  The connection to the Directory Server was " +
                    "closed while waiting for a response to the shutdown " +
                    "request.  This likely means that the server has started " +
                    "the shudown process.");
    registerMessage(MSGID_STOPDS_IO_ERROR,
                    "ERROR:  An I/O error occurred while attempting to " +
                    "communicate with the Directory Server:  %s.");
    registerMessage(MSGID_STOPDS_DECODE_ERROR,
                    "ERROR:  An error occurred while trying to decode the " +
                    "response from the server:  %s.");
    registerMessage(MSGID_STOPDS_INVALID_RESPONSE_TYPE,
                    "ERROR:  Expected an add response message but got a %s " +
                    "message instead.");
    registerMessage(MSGID_STOPDS_SERVER_ALREADY_STOPPED,
                    "Server already stopped.");
    registerMessage(MSGID_STOPDS_GOING_TO_STOP,
                    "Stopping Server...\n");


    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_LDIF_FILE,
                    "Specifies the LDIF file containing the data to search.  " +
                    "Multiple files may be specified by providing the option " +
                    "multiple times.  If no files are provided, the data " +
                    "will be read from standard input.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_BASEDN,
                    "The base DN for the search.  Multiple base DNs may be " +
                    "specified by providing the option multiple times.  If " +
                    "no base DN is provided, then the root DSE will be used.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_SCOPE,
                    "The scope for the search.  It must be one of 'base', " +
                    "'one', 'sub', or 'subordinate'.  If it is not provided, " +
                    "then 'sub' will be used.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file, " +
                    "which will enable the use of the schema definitions " +
                    "when processing the searches.  If it is not provided, " +
                    "then schema processing will not be available.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_FILTER_FILE,
                    "The path to the file containing the search filter(s) " +
                    "to use.  If this is not provided, then the filter must " +
                    "be provided on the command line after all configuration " +
                    "options.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_OUTPUT_FILE,
                    "The path to the output file to which the matching " +
                    "entries should be written.  If this is not provided, " +
                    "then the data will be written to standard output.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_OVERWRITE_EXISTING,
                    "Indicates that any existing output file should be " +
                    "overwritten rather than appending to it.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_DONT_WRAP,
                    "Indicates that long lines should not be wrapped.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_SIZE_LIMIT,
                    "Specifies the maximum number of matching entries to " +
                    "return.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_TIME_LIMIT,
                    "Specifies the maximum length of time (in seconds) to " +
                    "spend processing.");
    registerMessage(MSGID_LDIFSEARCH_DESCRIPTION_USAGE,
                    "Displays usage information for this program.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LDIFSEARCH_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LDIFSEARCH_NO_FILTER,
                    "No search filter was specified.  Either a filter file " +
                    "or an individual search filter must be provided.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_INITIALIZE_JMX,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server JMX subsystem based on the information " +
                    "in configuration file %s:  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_INITIALIZE_CONFIG,
                    "An error occurred while attempting to process the " +
                    "Directory Server configuration file %s:  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_INITIALIZE_SCHEMA,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server schema based on the information in " +
                    "configuration file %s:  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_PARSE_FILTER,
                    "An error occurred while attempting to parse search " +
                    "filter '%s':  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_PARSE_BASE_DN,
                    "An error occurred while attempting to parse base DN " +
                    "'%s':  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_PARSE_TIME_LIMIT,
                    "An error occurred while attempting to parse the " +
                    "time limit as an integer:  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_PARSE_SIZE_LIMIT,
                    "An error occurred while attempting to parse the " +
                    "size limit as an integer:  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_CREATE_READER,
                    "An error occurred while attempting to create the LDIF " +
                    "reader:  %s.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_CREATE_WRITER,
                    "An error occurred while attempting to create the LDIF " +
                    "writer used to return matching entries:  %s.");
    registerMessage(MSGID_LDIFSEARCH_TIME_LIMIT_EXCEEDED,
                    "The specified time limit has been exceeded during " +
                    "search processing.");
    registerMessage(MSGID_LDIFSEARCH_SIZE_LIMIT_EXCEEDED,
                    "The specified size limit has been exceeded during " +
                    "search processing.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_READ_ENTRY_RECOVERABLE,
                    "An error occurred while attempting to read an entry " +
                    "from the LDIF content:  %s.  Skipping this entry and " +
                    "continuing processing.");
    registerMessage(MSGID_LDIFSEARCH_CANNOT_READ_ENTRY_FATAL,
                    "An error occurred while attempting to read an entry " +
                    "from the LDIF content:  %s.  Unable to continue " +
                    "processing.");
    registerMessage(MSGID_LDIFSEARCH_ERROR_DURING_PROCESSING,
                    "An unexpected error occurred during search processing:  " +
                    "%s.");


    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_SOURCE_LDIF,
                    "Specifies the LDIF file to use as the source data.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_TARGET_LDIF,
                    "Specifies the LDIF file to use as the target data.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_OUTPUT_LDIF,
                    "Specifies the file to which the output should be " +
                    "written.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_SINGLE_VALUE_CHANGES,
                    "Indicates that each attribute-level change should be " +
                    "written as a separate modification per attribute value " +
                    "rather than one modification per entry.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_OVERWRITE_EXISTING,
                    "Indicates that any existing output file should be " +
                    "overwritten rather than appending to it.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file, " +
                    "which will enable the use of the schema definitions " +
                    "when processing the LDIF data.  If it is not provided, " +
                    "then schema processing will not be available.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_LDIFDIFF_DESCRIPTION_USAGE,
                    "Displays usage information for this program.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LDIFDIFF_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_INITIALIZE_JMX,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server JMX subsystem based on the information " +
                    "in configuration file %s:  %s.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_INITIALIZE_CONFIG,
                    "An error occurred while attempting to process the " +
                    "Directory Server configuration file %s:  %s.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_INITIALIZE_SCHEMA,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server schema based on the information in " +
                    "configuration file %s:  %s.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_OPEN_SOURCE_LDIF,
                    "An error occurred while attempting to open source LDIF " +
                    "%s:  %s.");
    registerMessage(MSGID_LDIFDIFF_ERROR_READING_SOURCE_LDIF,
                    "An error occurred while reading the contents of source " +
                    "LDIF %s:  %s.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_OPEN_TARGET_LDIF,
                    "An error occurred while attempting to open target LDIF " +
                    "%s:  %s.");
    registerMessage(MSGID_LDIFDIFF_ERROR_READING_TARGET_LDIF,
                    "An error occurred while reading the contents of target " +
                    "LDIF %s:  %s.");
    registerMessage(MSGID_LDIFDIFF_CANNOT_OPEN_OUTPUT,
                    "An error occurred while attempting to open the LDIF " +
                    "writer for the diff output:  %s.");
    registerMessage(MSGID_LDIFDIFF_NO_DIFFERENCES,
                    "No differences were detected between the source and " +
                    "target LDIF files.");
    registerMessage(MSGID_LDIFDIFF_ERROR_WRITING_OUTPUT,
                    "An error occurred while attempting to write the diff " +
                    "output:  %s.");


    registerMessage(MSGID_CONFIGDS_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_LDAP_PORT,
                    "Specifies the port on which the Directory Server should " +
                    "listen for LDAP communication.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_BASE_DN,
                    "Specifies the base DN for user information in the " +
                    "Directory Server.  Multiple base DNs may be provided " +
                    "by using this option multiple times.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_ROOT_DN,
                    "Specifies the DN for the initial root user for the " +
                    "Directory Server.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_ROOT_PW,
                    "Specifies the password for the initial root user for " +
                    "the Directory Server.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_ROOT_PW_FILE,
                    "Specifies the path to a file containing the password " +
                    "for the initial root user for the Directory Server.");
    registerMessage(MSGID_CONFIGDS_DESCRIPTION_USAGE,
                    "Displays usage information for this program.");
    registerMessage(MSGID_CONFIGDS_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_CONFIGDS_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_ACQUIRE_SERVER_LOCK,
                    "An error occurred while attempting to acquire the " +
                    "server-wide lock file %s:  %s.  This generally means " +
                    "that the Directory Server is running, or another tool " +
                    "that requires exclusive access to the server is in use.");
    registerMessage(MSGID_CONFIGDS_CANNOT_INITIALIZE_JMX,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server JMX subsystem based on the information " +
                    "in configuration file %s:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_INITIALIZE_CONFIG,
                    "An error occurred while attempting to process the " +
                    "Directory Server configuration file %s:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_INITIALIZE_SCHEMA,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server schema based on the information in " +
                    "configuration file %s:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_PARSE_BASE_DN,
                    "An error occurred while attempting to parse base DN " +
                    "value \"%s\" as a DN:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_PARSE_ROOT_DN,
                    "An error occurred while attempting to parse root DN " +
                    "value \"%s\" as a DN:  %s.");
    registerMessage(MSGID_CONFIGDS_NO_ROOT_PW,
                    "The DN for the initial root user was provided, but no " +
                    "corresponding password was given.  If the root DN is " +
                    "specified then the password must also be provided.");
    registerMessage(MSGID_CONFIGDS_CANNOT_UPDATE_BASE_DN,
                    "An error occurred while attempting to update the base " +
                    "DN(s) for user data in the Directory Server:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_UPDATE_LDAP_PORT,
                    "An error occurred while attempting to update the port " +
                    "on which to listen for LDAP communication:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_UPDATE_ROOT_USER,
                    "An error occurred while attempting to update the entry " +
                    "for the initial Directory Server root user:  %s.");
    registerMessage(MSGID_CONFIGDS_CANNOT_WRITE_UPDATED_CONFIG,
                    "An error occurred while writing the updated Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_CONFIGDS_NO_CONFIG_CHANGES,
                    "ERROR:  No configuration changes were specified.");
    registerMessage(MSGID_CONFIGDS_WROTE_UPDATED_CONFIG,
                    "Successfully wrote the updated Directory Server " +
                    "configuration.");


    registerMessage(MSGID_INSTALLDS_DESCRIPTION_TESTONLY,
                    "Just verify that the JVM can be started properly.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_PROGNAME,
                    "The setup command used to invoke this program.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_CLI,
                    "Launch the installer in command-line mode (no GUI).");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_SILENT,
                    "Perform a silent installation.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_BASEDN,
                    "Specifies the base DN for user information in the " +
                    "Directory Server.  Multiple base DNs may be provided " +
                    "by using this option multiple times.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_ADDBASE,
                    "Indicates whether to create the base entry in the " +
                    "Directory Server database.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_IMPORTLDIF,
                    "Specifies the path to an LDIF file containing data that " +
                    "should be added to the Directory Server database.  " +
                    "Multiple LDIF files may be provided by using this " +
                    "option multiple times.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_SAMPLE_DATA,
                    "Specifies that the database should be populated with " +
                    "the specified number of sample entries.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_LDAPPORT,
                    "Specifies the port on which the Directory Server should " +
                    "listen for LDAP communication.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_SKIPPORT,
                    "Skip the check to determine whether the specified LDAP " +
                    "port is usable.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_ROOTDN,
                    "Specifies the DN for the initial root user for the " +
                    "Directory Server.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_ROOTPW,
                    "Specifies the password for the initial root user for " +
                    "the Directory Server.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_ROOTPWFILE,
                    "Specifies the path to a file containing the password " +
                    "for the initial root user for the Directory Server.");
    registerMessage(MSGID_INSTALLDS_DESCRIPTION_HELP,
                    "Displays usage information for this program.");
    registerMessage(MSGID_INSTALLDS_NO_CONFIG_FILE,
                    "ERROR:  No configuration file path was provided (use " +
                    "the %s argument).");
    registerMessage(MSGID_INSTALLDS_INITIALIZING,
                    "Please wait while the setup program initializes....");
    registerMessage(MSGID_INSTALLDS_CANNOT_INITIALIZE_JMX,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server JMX subsystem based on the information " +
                    "in configuration file %s:  %s.");
    registerMessage(MSGID_INSTALLDS_CANNOT_INITIALIZE_CONFIG,
                    "An error occurred while attempting to process the " +
                    "Directory Server configuration file %s:  %s.");
    registerMessage(MSGID_INSTALLDS_CANNOT_INITIALIZE_SCHEMA,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server schema based on the information in " +
                    "configuration file %s:  %s.");
    registerMessage(MSGID_INSTALLDS_CANNOT_PARSE_DN,
                    "An error occurred while attempting to parse the string " +
                    "\"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_INSTALLDS_PROMPT_BASEDN,
                    "What do you wish to use as the base DN for the " +
                    "directory data?");
    registerMessage(MSGID_INSTALLDS_PROMPT_IMPORT,
                    "Do you wish to populate the directory database with " +
                    "information from an existing LDIF file?");
    registerMessage(MSGID_INSTALLDS_PROMPT_IMPORT_FILE,
                    "Please specify the path to the LDIF file containing " +
                    "the data to import.");
    registerMessage(MSGID_INSTALLDS_TWO_CONFLICTING_ARGUMENTS,
                    "ERROR:  You may not provide both the %s and the %s " +
                    "arguments at the same time.");
    registerMessage(MSGID_INSTALLDS_PROMPT_ADDBASE,
                    "Would you like to have the base %s entry automatically " +
                    "created in the directory database?");
    registerMessage(MSGID_INSTALLDS_PROMPT_LDAPPORT,
                    "On which port would you like the Directory Server to " +
                    "accept connections from LDAP clients?");
    registerMessage(MSGID_INSTALLDS_CANNOT_BIND_TO_PRIVILEGED_PORT,
                    "ERROR:  Unable to bind to port %d.  This port may " +
                    "already be in use, or you may not have permission to " +
                    "bind to it.  On UNIX-based operating systems, non-root " +
                    "users may not be allowed to bind to ports 1 through " +
                    "1024.");
    registerMessage(MSGID_INSTALLDS_CANNOT_BIND_TO_PORT,
                    "ERROR:  Unable to bind to port %d.  This port may " +
                    "already be in use, or you may not have permission to " +
                    "bind to it.");
    registerMessage(MSGID_INSTALLDS_PROMPT_ROOT_DN,
                    "What would you like to use as the initial root user DN " +
                    "for the Directory Server?");
    registerMessage(MSGID_INSTALLDS_NO_ROOT_PASSWORD,
                    "ERROR:  No password was provided for the initial root "+
                    "user.  When performing a silent installation, this must " +
                    "be provided using either the %s or the %s argument.");
    registerMessage(MSGID_INSTALLDS_PROMPT_ROOT_PASSWORD,
                    "Please provide the password to use for the initial root " +
                    "user");
    registerMessage(MSGID_INSTALLDS_PROMPT_CONFIRM_ROOT_PASSWORD,
                    "Please re-enter the password for confirmation");
    registerMessage(MSGID_INSTALLDS_STATUS_CONFIGURING_DS,
                    "Applying the requested configuration to the " +
                    "Directory Server....");
    registerMessage(MSGID_INSTALLDS_STATUS_CREATING_BASE_LDIF,
                    "Creating a temporary LDIF file with the initial base " +
                    "entry contents....");
    registerMessage(MSGID_INSTALLDS_CANNOT_CREATE_BASE_ENTRY_LDIF,
                    "An error occurred while attempting to create the " +
                    "base LDIF file:  %s.");
    registerMessage(MSGID_INSTALLDS_STATUS_IMPORTING_LDIF,
                    "Importing the LDIF data into the Directory Server " +
                    "database....");
    registerMessage(MSGID_INSTALLDS_IMPORT_SUCCESSFUL,
                    "Import complete.");
    registerMessage(MSGID_INSTALLDS_IMPORT_UNSUCCESSFUL,
                    "Import failed.");
    registerMessage(MSGID_INSTALLDS_STATUS_SUCCESS,
                    "The " + SHORT_NAME + " setup process has completed " +
                    "successfully.");
    registerMessage(MSGID_INSTALLDS_PROMPT_VALUE_YES, "yes");
    registerMessage(MSGID_INSTALLDS_PROMPT_VALUE_NO, "no");
    registerMessage(MSGID_INSTALLDS_INVALID_YESNO_RESPONSE,
                    "ERROR:  The provided value could not be interpreted as " +
                    "a yes or no response.  Please enter a response of " +
                    "either \"yes\" or \"no\".");
    registerMessage(MSGID_INSTALLDS_INVALID_INTEGER_RESPONSE,
                    "ERROR:  The provided response could not be interpreted " +
                    "as an integer.  Please provide the repsonse as an " +
                    "integer value.");
    registerMessage(MSGID_INSTALLDS_INTEGER_BELOW_LOWER_BOUND,
                    "ERROR:  The provided value is less than the lowest " +
                    "allowed value of %d.");
    registerMessage(MSGID_INSTALLDS_INTEGER_ABOVE_UPPER_BOUND,
                    "ERROR:  The provided value is greater than the largest " +
                    "allowed value of %d.");
    registerMessage(MSGID_INSTALLDS_INVALID_DN_RESPONSE,
                    "ERROR:  The provided response could not be interpreted " +
                    "as an LDAP DN.");
    registerMessage(MSGID_INSTALLDS_INVALID_STRING_RESPONSE,
                    "ERROR:  The response value may not be an empty string.");
    registerMessage(MSGID_INSTALLDS_INVALID_PASSWORD_RESPONSE,
                    "ERROR:  The password value may not be an empty string.");
    registerMessage(MSGID_INSTALLDS_PASSWORDS_DONT_MATCH,
                    "ERROR:  The provided password values do not match.");
    registerMessage(MSGID_INSTALLDS_ERROR_READING_FROM_STDIN,
                    "ERROR:  Unexpected failure while reading from standard " +
                    "input:  %s.");
    registerMessage(MSGID_INSTALLDS_HEADER_POPULATE_TYPE,
                    "Options for populating the database:");
    registerMessage(MSGID_INSTALLDS_POPULATE_OPTION_BASE_ONLY,
                    "Only create the base entry");
    registerMessage(MSGID_INSTALLDS_POPULATE_OPTION_LEAVE_EMPTY,
                    "Leave the database empty");
    registerMessage(MSGID_INSTALLDS_POPULATE_OPTION_IMPORT_LDIF,
                    "Import data from an LDIF file");
    registerMessage(MSGID_INSTALLDS_POPULATE_OPTION_GENERATE_SAMPLE,
                    "Load automatically-generated sample data");
    registerMessage(MSGID_INSTALLDS_PROMPT_POPULATE_CHOICE,
                    "Database population selection:");
    registerMessage(MSGID_INSTALLDS_NO_SUCH_LDIF_FILE,
                    "ERROR:  The specified LDIF file %s does not exist.");
    registerMessage(MSGID_INSTALLDS_PROMPT_NUM_ENTRIES,
                    "Please specify the number of user entries to generate:");
    registerMessage(MSGID_INSTALLDS_CANNOT_CREATE_TEMPLATE_FILE,
                    "ERROR:  Cannot create the template file for generating " +
                    "sample data:  %s.");


    registerMessage(MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_COUNT,
                    "Invalid number of arguments provided for tag %s on line " +
                    "number %d of the template file:  expected %d, got %d.");
    registerMessage(MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT,
                    "Invalid number of arguments provided for tag %s on line " +
                    "number %d of the template file:  expected between %d " +
                    "and %d, got %d.");
    registerMessage(MSGID_MAKELDIF_TAG_UNDEFINED_ATTRIBUTE,
                    "Undefined attribute %s referenced on line %d of the " +
                    "template file.");
    registerMessage(MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND,
                    "Value %d is below the lowest allowed value of %d for " +
                    "tag %s on line %d of the template file.");
    registerMessage(MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER,
                    "Cannot parse value \"%s\" as an integer for tag %s on " +
                    "line %d of the template file.");
    registerMessage(MSGID_MAKELDIF_TAG_INTEGER_ABOVE_UPPER_BOUND,
                    "Value %d is above the largest allowed value of %d for " +
                    "tag %s on line %d of the template file.");
    registerMessage(MSGID_MAKELDIF_TAG_INVALID_EMPTY_STRING_ARGUMENT,
                    "Argument %d for tag %s on line number %d may not be an " +
                    "empty string.");
    registerMessage(MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_BOOLEAN,
                    "Cannot parse value \"%s\" as a Boolean value for tag %s " +
                    "on line %d of the template file.  The value must be " +
                    "either 'true' or 'false'.");
    registerMessage(MSGID_MAKELDIF_UNDEFINED_BRANCH_SUBORDINATE,
                    "The branch with entry DN %s references a subordinate " +
                    "template named %s which is not defined in the template " +
                    "file.");
    registerMessage(MSGID_MAKELDIF_CANNOT_LOAD_TAG_CLASS,
                    "Unable to load class %s for use as a MakeLDIF tag.");
    registerMessage(MSGID_MAKELDIF_CANNOT_INSTANTIATE_TAG,
                    "Cannot instantiate class %s as a MakeLDIF tag.");
    registerMessage(MSGID_MAKELDIF_CONFLICTING_TAG_NAME,
                    "Cannot register the tag defined in class %s because " +
                    "the tag name %s conflicts with the name of another " +
                    "tag that has already been registered.");
    registerMessage(MSGID_MAKELDIF_WARNING_UNDEFINED_CONSTANT,
                    "Possible reference to an undefined constant %s on line " +
                    "%d.");
    registerMessage(MSGID_MAKELDIF_DEFINE_MISSING_EQUALS,
                    "The constant definition on line %d is missing an " +
                    "equal sign to delimit the constant name from the value.");
    registerMessage(MSGID_MAKELDIF_DEFINE_NAME_EMPTY,
                    "The constant definition on line %d does not include a " +
                    "name for the constant.");
    registerMessage(MSGID_MAKELDIF_CONFLICTING_CONSTANT_NAME,
                    "The definition for constant %s on line %d conflicts " +
                    "with an earlier constant definition included in the " +
                    "template.");
    registerMessage(MSGID_MAKELDIF_WARNING_DEFINE_VALUE_EMPTY,
                    "Constant %s defined on line %d has not been assigned a " +
                    "value.");
    registerMessage(MSGID_MAKELDIF_CONFLICTING_BRANCH_DN,
                    "The branch definition %s starting on line %d conflicts " +
                    "with an earlier branch definition contained in the " +
                    "template file.");
    registerMessage(MSGID_MAKELDIF_CONFLICTING_TEMPLATE_NAME,
                    "The template definition %s starting on line %d " +
                    "conflicts with an earlier template definition contained " +
                    "in the template file.");
    registerMessage(MSGID_MAKELDIF_UNEXPECTED_TEMPLATE_FILE_LINE,
                    "Unexpected template line \"%s\" encountered on line %d " +
                    "of the template file.");
    registerMessage(MSGID_MAKELDIF_UNDEFINED_TEMPLATE_SUBORDINATE,
                    "The template named %s references a subordinate template " +
                    "named %s which is not defined in the template file.");
    registerMessage(MSGID_MAKELDIF_TEMPLATE_MISSING_RDN_ATTR,
                    "The template named %s includes RDN attribute %s that " +
                    "is not assigned a value in that template.");
    registerMessage(MSGID_MAKELDIF_CANNOT_DECODE_BRANCH_DN,
                    "Unable to decode branch DN \"%s\" on line %d of the " +
                    "template file.");
    registerMessage(MSGID_MAKELDIF_BRANCH_SUBORDINATE_TEMPLATE_NO_COLON,
                    "Subordinate template definition on line %d for branch " +
                    "%s is missing a colon to separate the template name " +
                    "from the number of entries.");
    registerMessage(MSGID_MAKELDIF_BRANCH_SUBORDINATE_INVALID_NUM_ENTRIES,
                    "Subordinate template definition on line %d for branch " +
                    "%s specified invalid number of entries %d for template " +
                    "%s.");
    registerMessage(MSGID_MAKELDIF_BRANCH_SUBORDINATE_ZERO_ENTRIES,
                    "Subordinate template definition on line %d for branch " +
                    "%s specifies that zero entries of type %s should be " +
                    "generated.");
    registerMessage(MSGID_MAKELDIF_BRANCH_SUBORDINATE_CANT_PARSE_NUMENTRIES,
                    "Unable to parse the number of entries for template %s " +
                    "as an integer for the subordinate template definition " +
                    "on line %d for branch %s.");
    registerMessage(MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_TEMPLATE_NO_COLON,
                    "Subordinate template definition on line %d for template " +
                    "%s is missing a colon to separate the template name " +
                    "from the number of entries.");
    registerMessage(MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_INVALID_NUM_ENTRIES,
                    "Subordinate template definition on line %d for template " +
                    "%s specified invalid number of entries %d for " +
                    "subordinate template %s.");
    registerMessage(MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_ZERO_ENTRIES,
                    "Subordinate template definition on line %d for template " +
                    "%s specifies that zero entries of type %s should be " +
                    "generated.");
    registerMessage(MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_CANT_PARSE_NUMENTRIES,
                    "Unable to parse the number of entries for template %s " +
                    "as an integer for the subordinate template definition " +
                    "on line %d for template %s.");
    registerMessage(MSGID_MAKELDIF_NO_COLON_IN_BRANCH_EXTRA_LINE,
                    "There is no colon to separate the attribute name from " +
                    "the value pattern on line %s of the template file in " +
                    "the definition for branch %s.");
    registerMessage(MSGID_MAKELDIF_NO_ATTR_IN_BRANCH_EXTRA_LINE,
                    "There is no attribute name before the colon on line %d " +
                    "of the template file in the definition for branch %s.");
    registerMessage(MSGID_MAKELDIF_NO_VALUE_IN_BRANCH_EXTRA_LINE,
                    "The value pattern for line %s of the template file in " +
                    "the definition for branch %s is empty.");
    registerMessage(MSGID_MAKELDIF_INCOMPLETE_TAG,
                    "Line %d of the template file contains an incomplete " +
                    "tag that starts with either '<' or '{' but does get " +
                    "closed.");
    registerMessage(MSGID_MAKELDIF_NO_COLON_IN_TEMPLATE_LINE,
                    "There is no colon to separate the attribute name from " +
                    "the value pattern on line %s of the template file in " +
                    "the definition for template %s.");
    registerMessage(MSGID_MAKELDIF_NO_ATTR_IN_TEMPLATE_LINE,
                    "There is no attribute name before the colon on line %d " +
                    "of the template file in the definition for template %s.");
    registerMessage(MSGID_MAKELDIF_NO_VALUE_IN_TEMPLATE_LINE,
                    "The value pattern for line %s of the template file in " +
                    "the definition for template %s is empty.");
    registerMessage(MSGID_MAKELDIF_NO_SUCH_TAG,
                    "An undefined tag %s is referenced on line %d of the " +
                    "template file.");
    registerMessage(MSGID_MAKELDIF_CANNOT_INSTANTIATE_NEW_TAG,
                    "An unexpected error occurred while trying to create a " +
                    "new instance of tag %s referenced on line %d of the " +
                    "template file:  %s.");
    registerMessage(MSGID_MAKELDIF_TAG_NOT_ALLOWED_IN_BRANCH,
                    "Tag %s referenced on line %d of the template file is " +
                    "not allowed for use in branch definitions.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_RESOURCE_PATH,
                    "Specifies the path to look for MakeLDIF resources " +
                    "(e.g., data files) not found in the current working " +
                    "directory or template directory path.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_TEMPLATE,
                    "The path to the template file with information about " +
                    "the LDIF data to generate.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_LDIF,
                    "The path to the LDIF file to be written.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_SEED,
                    "The seed to use to initialize the random number " +
                    "generator.");
    registerMessage(MSGID_MAKELDIF_DESCRIPTION_HELP,
                    "Show this usage information.");
    registerMessage(MSGID_MAKELDIF_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_MAKELDIF_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_MAKELDIF_CANNOT_INITIALIZE_JMX,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server JMX subsystem based on the information " +
                    "in configuration file %s:  %s.");
    registerMessage(MSGID_MAKELDIF_CANNOT_INITIALIZE_CONFIG,
                    "An error occurred while attempting to process the " +
                    "Directory Server configuration file %s:  %s.");
    registerMessage(MSGID_MAKELDIF_CANNOT_INITIALIZE_SCHEMA,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server schema based on the information in " +
                    "configuration file %s:  %s.");
    registerMessage(MSGID_MAKELDIF_IOEXCEPTION_DURING_PARSE,
                    "An error occurred while attempting to read the " +
                    "template file:  %s.");
    registerMessage(MSGID_MAKELDIF_EXCEPTION_DURING_PARSE,
                    "An error occurred while attempting to parse the " +
                    "template file:  %s.");
    registerMessage(MSGID_MAKELDIF_TAG_INVALID_FORMAT_STRING,
                    "Cannot parse value \"%s\" as an valid format string for " +
                    "tag %s on line %d of the template file.");
    registerMessage(MSGID_MAKELDIF_TAG_NO_RANDOM_TYPE_ARGUMENT,
                    "The random tag on line %d of the template file does " +
                    "not include an argument to specify the type of random " +
                    "value that should be generated.");
    registerMessage(MSGID_MAKELDIF_TAG_WARNING_EMPTY_VALUE,
                    "The value generated from the random tag on line %d of " +
                    "the template file will always be an empty string.");
    registerMessage(MSGID_MAKELDIF_TAG_UNKNOWN_RANDOM_TYPE,
                    "The random tag on line %d of the template file " +
                    "references an unknown random type of %s.");
    registerMessage(MSGID_MAKELDIF_COULD_NOT_FIND_TEMPLATE_FILE,
                    "Could not find template file %s.");
    registerMessage(MSGID_MAKELDIF_NO_SUCH_RESOURCE_DIRECTORY,
                    "The specified resource directory %s could not be found.");
    registerMessage(MSGID_MAKELDIF_RESOURCE_DIRECTORY_NOT_DIRECTORY,
                    "The specified resource directory %s exists but is not a " +
                    "directory.");
    registerMessage(MSGID_MAKELDIF_TAG_CANNOT_FIND_FILE,
                    "Cannot find file %s referenced by tag %s on line %d of " +
                    "the template file.");
    registerMessage(MSGID_MAKELDIF_TAG_INVALID_FILE_ACCESS_MODE,
                    "Invalid file access mode %s for tag %s on line %d of " +
                    "the template file.  It must be either \"sequential\" or " +
                    "\"random\".");
    registerMessage(MSGID_MAKELDIF_TAG_CANNOT_READ_FILE,
                    "An error occurred while trying to read file %s " +
                    "referenced by tag %s on line %d of the template file:  " +
                    "%s.");
    registerMessage(MSGID_MAKELDIF_UNABLE_TO_CREATE_LDIF,
                    "An error occurred while attempting to open LDIF file %s " +
                    "for writing:  %s.");
    registerMessage(MSGID_MAKELDIF_ERROR_WRITING_LDIF,
                    "An error occurred while writing data to LDIF file %s:  " +
                    "%s.");
    registerMessage(MSGID_MAKELDIF_PROCESSED_N_ENTRIES,
                    "Processed %d entries.");
    registerMessage(MSGID_MAKELDIF_CANNOT_WRITE_ENTRY,
                    "An error occurred while attempting to write entry %s to " +
                    "LDIF:  %s.");
    registerMessage(MSGID_MAKELDIF_PROCESSING_COMPLETE,
                    "LDIF processing complete.  %d entries written.");


    registerMessage(MSGID_LDIFMODIFY_CANNOT_ADD_ENTRY_TWICE,
                    "Entry %s is added twice in the set of changes to apply, " +
                    "which is not supported by the LDIF modify tool.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_DELETE_AFTER_ADD,
                    "Entry %s cannot be deleted because it was previously " +
                    "added in the set of changes.  This is not supported by " +
                    "the LDIF modify tool.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_MODIFY_ADDED_OR_DELETED,
                    "Cannot modify entry %s because it was previously added " +
                    "or deleted in the set of changes.  This is not " +
                    "supported by the LDIF modify tool.");
    registerMessage(MSGID_LDIFMODIFY_MODDN_NOT_SUPPORTED,
                    "The modify DN operation targeted at entry %s cannot be " +
                    "processed because modify DN operations are not " +
                    "supported by the LDIF modify tool.");
    registerMessage(MSGID_LDIFMODIFY_UNKNOWN_CHANGETYPE,
                    "Entry %s has an unknown changetype of %s.");
    registerMessage(MSGID_LDIFMODIFY_ADD_ALREADY_EXISTS,
                    "Unable to add entry %s because it already exists in " +
                    "the data set.");
    registerMessage(MSGID_LDIFMODIFY_DELETE_NO_SUCH_ENTRY,
                    "Unable to delete entry %s because it does not exist " +
                    "in the data set.");
    registerMessage(MSGID_LDIFMODIFY_MODIFY_NO_SUCH_ENTRY,
                    "Unable to modify entry %s because it does not exist " +
                    "in the data set.");
    registerMessage(MSGID_LDIFMODIFY_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file, " +
                    "which will enable the use of the schema definitions " +
                    "when processing the updates.  If it is not provided, " +
                    "then schema processing will not be available.");
    registerMessage(MSGID_LDIFMODIFY_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_LDIFMODIFY_DESCRIPTION_SOURCE,
                    "Specifies the LDIF file containing the data to be " +
                    "updated.");
    registerMessage(MSGID_LDIFMODIFY_DESCRIPTION_CHANGES,
                    "Specifies he LDIF file containing the changes to apply.");
    registerMessage(MSGID_LDIFMODIFY_DESCRIPTION_TARGET,
                    "Specifies he file to which the updated data should be " +
                    "written.");
    registerMessage(MSGID_LDIFMODIFY_DESCRIPTION_HELP,
                    "Displays this usage information.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LDIFMODIFY_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_INITIALIZE_JMX,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server JMX subsystem based on the information " +
                    "in configuration file %s:  %s.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_INITIALIZE_CONFIG,
                    "An error occurred while attempting to process the " +
                    "Directory Server configuration file %s:  %s.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_INITIALIZE_SCHEMA,
                    "An error occurred while attempting to initialize the " +
                    "Directory Server schema based on the information in " +
                    "configuration file %s:  %s.");
    registerMessage(MSGID_LDIFMODIFY_SOURCE_DOES_NOT_EXIST,
                    "The source LDIF file %s does not exist.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_OPEN_SOURCE,
                    "Unable to open the source LDIF file %s:  %s.");
    registerMessage(MSGID_LDIFMODIFY_CHANGES_DOES_NOT_EXIST,
                    "The changes LDIF file %s does not exist.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_OPEN_CHANGES,
                    "Unable to open the changes LDIF file %s:  %s.");
    registerMessage(MSGID_LDIFMODIFY_CANNOT_OPEN_TARGET,
                    "Unable to open the target LDIF file %s for writing:  %s.");
    registerMessage(MSGID_LDIFMODIFY_ERROR_PROCESSING_LDIF,
                    "An error occurred while processing the requested " +
                    "changes:  %s.");


    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_HOST,
                    "Specifies the address of the Directory Server system.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_PORT,
                    "Specifies the port in which the Directory Server is " +
                    "listening for LDAP client connections.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_BIND_DN,
                    "Specifies the DN to use to bind to the server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_BIND_PW,
                    "Specifies the password to use to bind to the server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_BIND_PW_FILE,
                    "Specifies the path to a file containing the password to "+
                    "use to bind to the server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_AUTHZID,
                    "Specifies the authorization ID for the user entry whose " +
                    "password should be changed.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_PROVIDE_DN_FOR_AUTHZID,
                    "Indicates that the bind DN should be used as the " +
                    "authorization ID for the password modify operation.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_NEWPW,
                    "Specifies the new password to provide for the target " +
                    "user.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_NEWPWFILE,
                    "Specifies the path to a file containing the new " +
                    "password to provide for the target user.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_CURRENTPW,
                    "Specifies the current password for the target user.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE,
                    "Specifies the path to a file containing the current " +
                    "password for the target user.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_USE_SSL,
                    "Use SSL to secure the communication with the Directory " +
                    "Server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_USE_STARTTLS,
                    "Use StartTLS to secure the communication with the " +
                    "Directory Server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_BLIND_TRUST,
                    "Blindly trust any SSL certificate presented by the " +
                    "server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE,
                    "The path to the keystore to use when establishing " +
                    "SSL/TLS communication with the server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE_PIN,
                    "The PIN needed to access the contents of the keystore.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE_PINFILE,
                    "The path to a file containing the PIN needed " +
                    "to access the contents of the keystore.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE,
                    "The path to the trust store to use when establishing " +
                    "SSL/TLS communication with the server.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PIN,
                    "The PIN needed to access the contents of the trust " +
                    "store.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PINFILE,
                    "The path to a file containing the PIN needed to access" +
                    "the contents of the trust store.");
    registerMessage(MSGID_LDAPPWMOD_DESCRIPTION_USAGE,
                    "Show this usage information.");
    registerMessage(MSGID_LDAPPWMOD_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LDAPPWMOD_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LDAPPWMOD_CONFLICTING_ARGS,
                    "The %s and %s arguments may not be provided together.");
    registerMessage(MSGID_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER,
                    "If either a bind DN or bind password is provided, then " +
                    "the other must be given as well.");
    registerMessage(MSGID_LDAPPWMOD_ANON_REQUIRES_AUTHZID_AND_CURRENTPW,
                    "If a bind DN and password are not provided, then an " +
                    "authorization ID and current password must be given.");
    registerMessage(MSGID_LDAPPWMOD_DEPENDENT_ARGS,
                    "If the %s argument is provided, then the  %s argument " +
                    "must also be given.");
    registerMessage(MSGID_LDAPPWMOD_ERROR_INITIALIZING_SSL,
                    "Unable to initialize SSL/TLS support:  %s.");
    registerMessage(MSGID_LDAPPWMOD_CANNOT_CONNECT,
                    "An error occurred while attempting to connect to the" +
                    "Directory Server:  %s.");
    registerMessage(MSGID_LDAPPWMOD_CANNOT_SEND_PWMOD_REQUEST,
                    "Unable to send the LDAP password modify request:  %s.");
    registerMessage(MSGID_LDAPPWMOD_CANNOT_READ_PWMOD_RESPONSE,
                    "Unable to read the LDAP password modify response:  %s.");
    registerMessage(MSGID_LDAPPWMOD_FAILED,
                    "The LDAP password modify operation failed with result " +
                    "code %d.");
    registerMessage(MSGID_LDAPPWMOD_FAILURE_ERROR_MESSAGE,
                    "Error Message:  %s.");
    registerMessage(MSGID_LDAPPWMOD_FAILURE_MATCHED_DN,
                    "Matched DN:  %s.");
    registerMessage(MSGID_LDAPPWMOD_SUCCESSFUL,
                    "The LDAP password modify operation was successful.");
    registerMessage(MSGID_LDAPPWMOD_ADDITIONAL_INFO,
                    "Additional Info:  %s.");
    registerMessage(MSGID_LDAPPWMOD_PWPOLICY_WARNING,
                    "Password Policy Warning:  %s = %d.");
    registerMessage(MSGID_LDAPPWMOD_PWPOLICY_ERROR,
                    "Password Policy Error:  %s.");
    registerMessage(MSGID_LDAPPWMOD_CANNOT_DECODE_PWPOLICY_CONTROL,
                    "Unable to decode the password policy response control:  " +
                     "%s.");
    registerMessage(MSGID_LDAPPWMOD_GENERATED_PASSWORD,
                    "Generated Password:  %s.");
    registerMessage(MSGID_LDAPPWMOD_UNRECOGNIZED_VALUE_TYPE,
                    "Unable to decode the password modify response value " +
                    "because it contained an invalid element type of %s.");
    registerMessage(MSGID_LDAPPWMOD_COULD_NOT_DECODE_RESPONSE_VALUE,
                    "Unable to decode the password modify response value:  " +
                    "%s.");


    registerMessage(MSGID_WAIT4DEL_DESCRIPTION_TARGET_FILE,
                    "Specifies the path to the file to watch for deletion.");
    registerMessage(MSGID_WAIT4DEL_DESCRIPTION_LOG_FILE,
                    "Specifies the path to a file containing log output to " +
                    "monitor.");
    registerMessage(MSGID_WAIT4DEL_DESCRIPTION_TIMEOUT,
                    "The maximum length of time in seconds to wait for the " +
                    "target file to be deleted before exiting.");
    registerMessage(MSGID_WAIT4DEL_DESCRIPTION_HELP,
                    "Displays this usage information.");
    registerMessage(MSGID_WAIT4DEL_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_WAIT4DEL_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_WAIT4DEL_CANNOT_OPEN_LOG_FILE,
                    "WARNING:  Unable to open log file %s for reading:  %s.");


    registerMessage(MSGID_LDAPCOMPARE_NO_DNS,
                    "No entry DNs provided for the compare operation.");
    registerMessage(MSGID_LDAPCOMPARE_NO_ATTR,
                    "No attribute was specified to use as the target for " +
                    "the comparison.");
    registerMessage(MSGID_LDAPCOMPARE_INVALID_ATTR_STRING,
                    "Invalid attribute string '%s'.  The attribute string " +
                    "must be in one of the following forms:  " +
                    "'attribute:value', 'attribute::base64value', or " +
                    "'attribute:<valueFilePath'");


    registerMessage(MSGID_LDAPSEARCH_PSEARCH_CHANGE_TYPE,
                    "# Persistent search change type:  %s");
    registerMessage(MSGID_LDAPSEARCH_PSEARCH_PREVIOUS_DN,
                    "# Persistent search previous entry DN:  %s");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_HEADER,
                    "# Account Usability Response Control");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_IS_USABLE,
                    "#   The account is usable.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION,
                    "#   Time until password expiration:  %s.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_NOT_USABLE,
                    "#   The account is not usable.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_ACCT_INACTIVE,
                    "#   The account has been deactivated.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_PW_RESET,
                    "#   The password has been reset.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_PW_EXPIRED,
                    "#   The password has expired.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE,
                    "#   Number of grace logins remaining:  %d.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_LOCKED,
                    "#   The account is locked.");
    registerMessage(MSGID_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK,
                    "#   Time until the account is unlocked:  %s.");
    registerMessage(MSGID_LDAPSEARCH_MATCHING_ENTRY_COUNT,
                    "# Total number of matching entries:  %d.");


    registerMessage(MSGID_TOOL_CONFLICTING_ARGS,
                    "You may not provide both the --%s and the --%s " +
                    "arguments.");
    registerMessage(MSGID_TOOL_INVALID_CONTROL_STRING,
                    "Invalid control specification '%s'.");
    registerMessage(MSGID_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS,
                    "SASL EXTERNAL authentication may only be requested if " +
                    "SSL or StartTLS is used.");
    registerMessage(MSGID_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE,
                    "SASL EXTERNAL authentication may only be used if a " +
                    "client certificate keystore is specified.");


    registerMessage(MSGID_BACKUPDB_TOOL_DESCRIPTION,
                    "This utility may be used to back up one or more " +
                    "Directory Server backends.");
    registerMessage(MSGID_CONFIGDS_TOOL_DESCRIPTION,
                    "This utility may be used to define a base configuration " +
                    "for the Directory Server.");
    registerMessage(MSGID_ENCPW_TOOL_DESCRIPTION,
                    "This utility may be used to encode user passwords with " +
                    "a specified storage scheme, or to determine whether a " +
                    "given clear-text value matches a provided encoded " +
                    "password.");
    registerMessage(MSGID_LDIFEXPORT_TOOL_DESCRIPTION,
                    "This utility may be used to export data from a " +
                    "Directory Server backend in LDIF form.");
    registerMessage(MSGID_LDIFIMPORT_TOOL_DESCRIPTION,
                    "This utility may be used to import LDIF data into a " +
                    "Directory Server backend.");
    registerMessage(MSGID_INSTALLDS_TOOL_DESCRIPTION,
                    "This utility may be used to define a base configuration " +
                    "for the Directory Server.");
    registerMessage(MSGID_LDAPCOMPARE_TOOL_DESCRIPTION,
                    "This utility may be used to perform LDAP compare " +
                    "operations in the Directory Server.");
    registerMessage(MSGID_LDAPDELETE_TOOL_DESCRIPTION,
                    "This utility may be used to perform LDAP delete " +
                    "operations in the Directory Server.");
    registerMessage(MSGID_LDAPMODIFY_TOOL_DESCRIPTION,
                    "This utility may be used to perform LDAP modify, add, " +
                    "delete, and modify DN operations in the Directory " +
                    "Server.");
    registerMessage(MSGID_LDAPPWMOD_TOOL_DESCRIPTION,
                    "This utility may be used to perform LDAP password " +
                    "modify operations in the Directory Server.");
    registerMessage(MSGID_LDAPSEARCH_TOOL_DESCRIPTION,
                    "This utility may be used to perform LDAP search " +
                    "operations in the Directory Server.");
    registerMessage(MSGID_LDIFDIFF_TOOL_DESCRIPTION,
                    "This utility may be used to compare two LDIF files " +
                    "and report the differences in LDIF format.");
    registerMessage(MSGID_LDIFMODIFY_TOOL_DESCRIPTION,
                    "This utility may be used to apply a set of modify, add, " +
                    "and delete operations against data in an LDIF file.");
    registerMessage(MSGID_LDIFSEARCH_TOOL_DESCRIPTION,
                    "This utility may be used to perform search operations " +
                    "against data in an LDIF file.");
    registerMessage(MSGID_MAKELDIF_TOOL_DESCRIPTION,
                    "This utility may be used to generate LDIF data based on " +
                    "a definition in a template file.");
    registerMessage(MSGID_RESTOREDB_TOOL_DESCRIPTION,
                    "This utility may be used to restore a backup of a " +
                    "Directory Server backend.");
    registerMessage(MSGID_STOPDS_TOOL_DESCRIPTION,
                    "This utility may be used to request that the Directory " +
                    "Server stop running or perform a restart.");
    registerMessage(MSGID_VERIFYINDEX_TOOL_DESCRIPTION,
                    "This utility may be used to ensure that index data is " +
                    "consistent within a backend based on the Berkeley DB " +
                    "Java Edition.");
    registerMessage(MSGID_WAIT4DEL_TOOL_DESCRIPTION,
                    "This utility may be used to wait for a file to be " +
                    "removed from the filesystem.");
    registerMessage(MSGID_LISTBACKENDS_TOOL_DESCRIPTION,
                    "This utility may be used to list the backends and base " +
                    "DNs configured in the Directory Server.");


    registerMessage(MSGID_LISTBACKENDS_DESCRIPTION_CONFIG_CLASS,
                    "The fully-qualified name of the Java class to use as " +
                    "the Directory Server configuration handler.  If this is " +
                    "not provided, then a default of " +
                    ConfigFileHandler.class.getName() + " will be used.");
    registerMessage(MSGID_LISTBACKENDS_DESCRIPTION_CONFIG_FILE,
                    "The path to the Directory Server configuration file, " +
                    "which will enable the use of the schema definitions " +
                    "when processing the updates.  If it is not provided, " +
                    "then schema processing will not be available.");
    registerMessage(MSGID_LISTBACKENDS_DESCRIPTION_BACKEND_ID,
                    "Backend ID of the backend for which to list the base DNs");
    registerMessage(MSGID_LISTBACKENDS_DESCRIPTION_BASE_DN,
                    "Base DN for which to list the backend ID");
    registerMessage(MSGID_LISTBACKENDS_DESCRIPTION_HELP,
                    "Display this usage information.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_LISTBACKENDS_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_LISTBACKENDS_SERVER_BOOTSTRAP_ERROR,
                    "An unexpected error occurred while attempting to " +
                    "bootstrap the Directory Server client-side code:  %s.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_LOAD_CONFIG,
                    "An error occurred while trying to load the Directory " +
                    "Server configuration:  %s.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_LOAD_SCHEMA,
                    "An error occurred while trying to load the Directory " +
                    "Server schema:  %s.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_GET_BACKENDS,
                    "An error occurred while trying to read backend " +
                    "information from the server configuration:  %s.");
    registerMessage(MSGID_LISTBACKENDS_INVALID_DN,
                    "The provided base DN value '%s' could not be parsed as " +
                    "a valid DN:  %s.");
    registerMessage(MSGID_LISTBACKENDS_NOT_BASE_DN,
                    "The provided DN '%s' is not a base DN for any backend " +
                    "configured in the Directory Server.");
    registerMessage(MSGID_LISTBACKENDS_NO_BACKEND_FOR_DN,
                    "The provided DN '%s' is not below any base DN for any " +
                    "of the backends configured in the Directory Server.");
    registerMessage(MSGID_LISTBACKENDS_DN_BELOW_BASE,
                    "The provided DN '%s' is below '%s' which is configured " +
                    "as a base DN for backend '%s'.");
    registerMessage(MSGID_LISTBACKENDS_BASE_FOR_ID,
                    "The provided DN '%s' is a base DN for backend '%s'.");
    registerMessage(MSGID_LISTBACKENDS_LABEL_BACKEND_ID,
                    "Backend ID");
    registerMessage(MSGID_LISTBACKENDS_LABEL_BASE_DN,
                    "Base DN");
    registerMessage(MSGID_LISTBACKENDS_NO_SUCH_BACKEND,
                    "There is no backend with ID '%s' in the server " +
                    "configuration.");
    registerMessage(MSGID_LISTBACKENDS_NO_VALID_BACKENDS,
                    "None of the provided backend IDs exist in the server " +
                    "configuration.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_DECODE_BACKEND_BASE_DN,
                    "Unable to decode the backend configuration base DN " +
                    "string \"%s\" as a valid DN:  %s.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY,
                    "Unable to retrieve the backend configuration base entry " +
                    "\"%s\" from the server configuration:  %s.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_DETERMINE_BACKEND_ID,
                    "Cannot determine the backend ID for the backend defined " +
                    "in configuration entry %s:  %s.");
    registerMessage(MSGID_LISTBACKENDS_CANNOT_DETERMINE_BASES_FOR_BACKEND,
                    "Unable to determine the set of base DNs defined in " +
                    "backend configuration entry %s:  %s.");


    registerMessage(MSGID_PROMPTTM_REJECTING_CLIENT_CERT,
                    "Rejecting client certificate chain because the prompt " +
                    "trust manager may only be used to trust server " +
                    "certificates.");
    registerMessage(MSGID_PROMPTTM_NO_SERVER_CERT_CHAIN,
                    "WARNING:  The server did not present a certificate " +
                    "chain.  Do you still wish to attempt connecting to the " +
                    "target server?");
    registerMessage(MSGID_PROMPTTM_CERT_EXPIRED,
                    "WARNING:  The server certificate is expired (expiration " +
                    "time:  %s).");
    registerMessage(MSGID_PROMPTTM_CERT_NOT_YET_VALID,
                    "WARNING:  The server certificate will not be valid " +
                    "until %s.");
    registerMessage(MSGID_PROMPTTM_SERVER_CERT,
                    "The server is using the following certificate:  " + EOL +
                    "    Subject DN:  %s" + EOL +
                    "    Issuer DN:  %s" + EOL +
                    "    Validity:  %s through %s" + EOL +
                    "Do you wish to trust this certificate and continue " +
                    "connecting to the server?");
    registerMessage(MSGID_PROMPTTM_YESNO_PROMPT,
                    "Please enter \"yes\" or \"no\":  ");
    registerMessage(MSGID_PROMPTTM_USER_REJECTED,
                    "The server certificate has been rejected by the user.");
  }
}

