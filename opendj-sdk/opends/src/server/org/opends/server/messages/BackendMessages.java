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
 * messages associated with the server backends.
 */
public class BackendMessages
{
  /**
   * The message ID for the message that will be used if an attempt is made to
   * de-register a sub-suffix that has multiple base DNs.  This takes two
   * arguments, which are the DN of the sub-suffix to deregister and the DN of
   * the parent suffix with which the sub-suffix is associated.
   */
  public static final int MSGID_BACKEND_CANNOT_REMOVE_MULTIBASE_SUB_SUFFIX =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_FATAL_ERROR | 1;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * initialize the root DSE backend without providing a configuration entry.
   * This does not take any arguments.
   */
  public static final int MSGID_ROOTDSE_CONFIG_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_FATAL_ERROR | 2;



  /**
   * The message ID for the string that will be used as the description of the
   * root DSE subordinate base DN attribute. This does not take any arguments.
   */
  public static final int MSGID_ROOTDSE_SUBORDINATE_BASE_DESCRIPTION =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 3;



  /**
   * The message ID for the message that will be used if a subordinate base DN
   * is defined for the root DSE that is not associated with any backend.  This
   * takes a single argument, which is the specified subordinate base DN.
   */
  public static final int MSGID_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_WARNING | 4;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * while trying to determine the set of subordinate base DNs to use for the
   * root DSE.  This takes a single argument, which is a string representation
   * of the exception that was thrown.
   */
  public static final int MSGID_ROOTDSE_SUBORDINATE_BASE_EXCEPTION =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_WARNING | 5;



  /**
   * The message ID for the message that will be used if the root DSE backend is
   * asked to retrieve an entry other than the root DSE.  This takes a single
   * argument, which is the DN of the entry that it was asked to retrieve.
   */
  public static final int MSGID_ROOTDSE_GET_ENTRY_NONROOT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_WARNING | 6;



  /**
   * The message ID for the message that will be used if an add operation is
   * attempted in the root DSE backend.  This takes a single argument, which is
   * the DN of the entry to add.
   */
  public static final int MSGID_ROOTDSE_ADD_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 7;



  /**
   * The message ID for the message that will be used if a delete operation is
   * attempted in the root DSE backend.  This takes a single argument, which is
   * the DN of the entry to delete.
   */
  public static final int MSGID_ROOTDSE_DELETE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 8;



  /**
   * The message ID for the message that will be used if a modify operation is
   * attempted in the root DSE backend.  This takes two arguments, which are the
   * DN of the entry that the user attempted to modify and the DN of the
   * configuration entry for the root DSE backend.
   */
  public static final int MSGID_ROOTDSE_MODIFY_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 9;



  /**
   * The message ID for the message that will be used if a modify DN operation
   * is attempted in the root DSE backend.  This takes a single argument, which
   * is the DN of the entry to rename.
   */
  public static final int MSGID_ROOTDSE_MODIFY_DN_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 10;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * process a search in the root DSE backend with a base that is not the DN of
   * the root DSE.  This takes three arguments, which are the connection ID and
   * operation ID for the search operation, and the requested base DN.
   */
  public static final int MSGID_ROOTDSE_INVALID_SEARCH_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 11;



  /**
   * The message ID for the message that will be used if an unexpected exception
   * is encountered while attempting to process a search below the root DSE.
   * This takes three arguments, which are the connection ID and
   * operation ID for the search operation, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_ROOTDSE_UNEXPECTED_SEARCH_FAILURE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 12;



  /**
   * The message ID for the message that will be used if a search operation
   * below the root DSE specifies an invalid scope.  This takes three arguments,
   * which are the connection ID and operation ID for the search operation, and
   * a string representation of the search scope.
   */
  public static final int MSGID_ROOTDSE_INVALID_SEARCH_SCOPE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 13;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to open the LDIF writer to export the root DSE.  This
   * takes a single argument, which is a stack trace of the exception that was
   * caught.
   */
  public static final int MSGID_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 14;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to write the root DSE entry to LDIF.  This takes a
   * single argument, which is a stack trace of the exception that was caught.
   */
  public static final int MSGID_ROOTDSE_UNABLE_TO_EXPORT_DSE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 15;



  /**
   * The message ID for the message that will be used if an LDIF import is
   * attempted for the root DSE backend.  This does not take any arguments.
   */
  public static final int MSGID_ROOTDSE_IMPORT_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 16;



  /**
   * The message ID for the message that will be used if a backup or restore
   * operation is attempted for the root DSE backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 17;



  /**
   * The message ID for the message that will be used if the root DSE
   * configuration is updated so that it will use the default set of suffixes
   * for searches below the root DSE.  This does not take any arguments.
   */
  public static final int MSGID_ROOTDSE_USING_SUFFIXES_AS_BASE_DNS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 18;



  /**
   * The message ID for the message that will be used if the root DSE
   * configuration is updated so that it will use a new set of base DNs for
   * searches below the root DSE.  This takes a single argument, which is a
   * string representation of the new set of base DNs.
   */
  public static final int MSGID_ROOTDSE_USING_NEW_SUBORDINATE_BASE_DNS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 19;



  /**
   * The message ID for the message that will be used if the root DSE
   * configuration is updated so that it will use a new set of user-defined
   * attributes when returning the root DSE.  This does not take any arguments.
   */
  public static final int MSGID_ROOTDSE_USING_NEW_USER_ATTRS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 20;



  /**
   * The message ID for the message that will be used if no configuration entry
   * is provided when attempting to initialize the monitor backend.  This does
   * not take any arguments.
   */
  public static final int MSGID_MONITOR_CONFIG_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 21;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the base DN for the server monitor information.  This
   * takes a single argument, which is a stack trace of the exception that was
   * caught.
   */
  public static final int MSGID_MONITOR_CANNOT_DECODE_MONITOR_ROOT_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 22;



  /**
   * The message ID for the message that will be used if an add operation is
   * attempted in the monitor backend.  This takes a single argument, which is
   * the DN of the entry to add.
   */
  public static final int MSGID_MONITOR_ADD_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 23;



  /**
   * The message ID for the message that will be used if a delete operation is
   * attempted in the monitor backend.  This takes a single argument, which is
   * the DN of the entry to delete.
   */
  public static final int MSGID_MONITOR_DELETE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 24;



  /**
   * The message ID for the message that will be used if a modify operation is
   * attempted in the monitor backend.  This takes two arguments, which are the
   * DN of the entry that the user attempted to modify and the DN of the
   * configuration entry for the monitor backend.
   */
  public static final int MSGID_MONITOR_MODIFY_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 25;



  /**
   * The message ID for the message that will be used if a modify DN operation
   * is attempted in the monitor backend.  This takes a single argument, which
   * is the DN of the entry to rename.
   */
  public static final int MSGID_MONITOR_MODIFY_DN_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 26;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to export the base monitor entry.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_MONITOR_UNABLE_TO_EXPORT_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 27;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to export an entry generated from a monitor provider.  This
   * takes two arguments, which are the name of the monitor provider and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_MONITOR_UNABLE_TO_EXPORT_PROVIDER_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 28;



  /**
   * The message ID for the message that will be used if an LDIF import is
   * attempted for the monitor backend.  This does not take any arguments.
   */
  public static final int MSGID_MONITOR_IMPORT_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 29;



  /**
   * The message ID for the message that will be used if a backup or restore
   * operation is attempted for the monitor backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 30;



  /**
   * The message ID for the message that will be used if the monitor
   * configuration is updated so that it will use a new set of user-defined
   * attributes when returning the base monitor entry.  This does not take any
   * arguments.
   */
  public static final int MSGID_MONITOR_USING_NEW_USER_ATTRS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 31;



  /**
   * The message ID for the message that will be used if the monitor backend is
   * requested to retrieve an entry with a null DN.  This does not take any
   * arguments.
   */
  public static final int MSGID_MONITOR_GET_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 32;



  /**
   * The message ID for the message that will be used if the monitor backend is
   * requested to retrieve an entry DN that is too deep.  This takes two
   * arguments, which are the DN of the requested entry and the DN of the base
   * monitor entry.
   */
  public static final int MSGID_MONITOR_BASE_TOO_DEEP =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 33;



  /**
   * The message ID for the message that will be used if the monitor backend is
   * requested to retrieve an entry not below the monitor base.  This takes two
   * arguments, which are the DN of the requested entry and the DN of the base
   * monitor entry.
   */
  public static final int MSGID_MONITOR_INVALID_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 34;



  /**
   * The message ID for the message that will be used if the monitor backend is
   * requested to retrieve an entry with a multivalued RDN.  This takes a single
   * argument, which is the DN of the requested entry.
   */
  public static final int MSGID_MONITOR_MULTIVALUED_RDN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 35;



  /**
   * The message ID for the message that will be used if the monitor backend is
   * requested to retrieve monitor entry with an RDN value that is not the name
   * of a monitor provider.  This takes a single argument, which is the
   * requested provider name.
   */
  public static final int MSGID_MONITOR_NO_SUCH_PROVIDER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 36;



  /**
   * The message ID for the string that will be used to represent the server
   * uptime in a human-readable string.  This takes four arguments, which are
   * the number of days, hours, minutes, and seconds that the server has been
   * online.
   */
  public static final int MSGID_MONITOR_UPTIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 37;



  /**
   * The message ID for the message that will be used if no configuration entry
   * is provided when attempting to initialize the schema backend.  This does
   * not take any arguments.
   */
  public static final int MSGID_SCHEMA_CONFIG_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 38;



  /**
   * The message ID for the message that will be used as the description for the
   * configuration attribute that is used to specify the base DN(s) for the
   * schema entries.  This does not take any arguments.
   */
  public static final int MSGID_SCHEMA_DESCRIPTION_ENTRY_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 39;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the base DN(s) for the schema entries.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_CANNOT_DETERMINE_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 40;



  /**
   * The message ID for the message that will be used if an add operation is
   * attempted in the schema backend.  This takes a single argument, which is
   * the DN of the entry to add.
   */
  public static final int MSGID_SCHEMA_ADD_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 41;



  /**
   * The message ID for the message that will be used if a delete operation is
   * attempted in the schema backend.  This takes a single argument, which is
   * the DN of the entry to delete.
   */
  public static final int MSGID_SCHEMA_DELETE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 42;



  /**
   * The message ID for the message that will be used if a modify operation is
   * attempted in the schema backend.  This takes two arguments, which are the
   * DN of the entry that the user attempted to modify and the DN of the
   * configuration entry for the schema backend.
   */
  public static final int MSGID_SCHEMA_MODIFY_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 43;



  /**
   * The message ID for the message that will be used if a modify DN operation
   * is attempted in the schema backend.  This takes a single argument, which is
   * the DN of the entry to rename.
   */
  public static final int MSGID_SCHEMA_MODIFY_DN_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 44;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * attempting to export the base schema entry.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_UNABLE_TO_EXPORT_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 45;



  /**
   * The message ID for the message that will be used if an LDIF import is
   * attempted for the schema backend.  This does not take any arguments.
   */
  public static final int MSGID_SCHEMA_IMPORT_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 46;



  /**
   * The message ID for the message that will be used if a backup or restore
   * operation is attempted for the schema backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_SCHEMA_BACKUP_AND_RESTORE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 47;



  /**
   * The message ID for the message that will be used if the schema backend is
   * requested to retrieve an entry that is not one of the schema entries.  This
   * takes two arguments, which are the DN of the requested entry and the DN of
   * the base schema entry.
   */
  public static final int MSGID_SCHEMA_INVALID_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 48;



  /**
   * The message ID for the message that will be used if an unexpected error
   * occurs while trying to open the LDIF writer to export the schema.  This
   * takes a single argument, which is a stack trace of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_UNABLE_TO_CREATE_LDIF_WRITER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 49;



  /**
   * The message ID for the message that will be used to indicate that an entry
   * has been successfully deregistered as a base DN for the schema information.
   * This takes a single argument, which is the DN that was deregistered.
   */
  public static final int MSGID_SCHEMA_DEREGISTERED_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 50;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to deregister a base DN for the schema information.  This takes
   * two arguments, which are the DN to deregister and string representation of
   * the exception that was caught.
   */
  public static final int MSGID_SCHEMA_CANNOT_DEREGISTER_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 51;



  /**
   * The message ID for the message that will be used to indicate that an entry
   * has been successfully registered as a base DN for the schema information.
   * This takes a single argument, which is the DN that was registered.
   */
  public static final int MSGID_SCHEMA_REGISTERED_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 52;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to register a base DN for the schema information.  This takes
   * two arguments, which are the DN to register and string representation of
   * the exception that was caught.
   */
  public static final int MSGID_SCHEMA_CANNOT_REGISTER_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 53;



  /**
   * The message ID for the message that will be used if the schema
   * configuration is updated so that it will use a new set of user-defined
   * attributes when returning the schema entry.  This does not take any
   * arguments.
   */
  public static final int MSGID_SCHEMA_USING_NEW_USER_ATTRS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 54;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain an entry lock in the backend code.  This takes a
   * single argument, which is the DN of the entry for which to obtain the lock.
   */
  public static final int MSGID_BACKEND_CANNOT_LOCK_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_FATAL_ERROR | 55;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the MAC provider for the schema backup.  This takes
   * two arguments, which are the name of the desired MAC algorithm and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_GET_MAC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 56;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the message digest for the schema backup.  This takes
   * two arguments, which are the name of the desired digest algorithm and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_GET_DIGEST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 57;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create the archive file for a schema backup.  This takes
   * three arguments, which are the name of the archive file, the path to the
   * archive directory, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_CREATE_ARCHIVE_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 58;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the cipher for the schema backup.  This takes two
   * arguments, which are the name of the desired cipher algorithm and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_GET_CIPHER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 59;



  /**
   * The message ID for the message that will be used for the message containing
   * the comment to include in the schema archive zip.  This takes two
   * arguments, which are the Directory Server product name and the backup ID.
   */
  public static final int MSGID_SCHEMA_BACKUP_ZIP_COMMENT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 60;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain a list of the schema files to include in the backup.
   * This takes two arguments, which are the path to the directory containing
   * the schema files and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_LIST_SCHEMA_FILES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 61;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to back up a schema file.  This takes two arguments, which are
   * the name of the schema file and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_BACKUP_SCHEMA_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 62;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to close the output stream for the schema archive.  This takes
   * three arguments, which are the name of the schema archive file, the path
   * to the directory containing that file, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_CLOSE_ZIP_STREAM =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the backup descriptor with information about the
   * schema backup.  This takes two arguments, which are the path to the backup
   * descriptor file and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 64;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * restore schema backup but the requested backup could not be found.  This
   * takes two arguments, which are the backup ID and the path to the backup
   * directory.
   */
  public static final int MSGID_SCHEMA_RESTORE_NO_SUCH_BACKUP =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 65;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * restore schema backup but it cannot be determined which archive file holds
   * that backup.  This takes two arguments, which are the backup ID and the
   * path to the backup directory.
   */
  public static final int MSGID_SCHEMA_RESTORE_NO_BACKUP_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 66;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * restore schema backup but the archive file does not exist.  This takes two
   * arguments, which are the backup ID and the expected path to the archive
   * file.
   */
  public static final int MSGID_SCHEMA_RESTORE_NO_SUCH_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 67;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether the backup archive exists.  This takes three
   * arguments, which are the backup ID, the expected path to the backup
   * archive, and a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_CHECK_FOR_ARCHIVE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 68;



  /**
   * The message ID for the message that will be used if a schema backup is
   * hashed but the digest algorithm is not known.  This takes a single
   * argument, which is the backup ID.
   */
  public static final int MSGID_SCHEMA_RESTORE_UNKNOWN_DIGEST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 69;



  /**
   * The message ID for the message that will be used if a schema backup has a
   * hash with an unknown or unsupported digest algorithm.  This takes two
   * arguments, which are the backup ID and the digest algorithm.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_GET_DIGEST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 70;



  /**
   * The message ID for the message that will be used if a schema backup is
   * signed but the MAC algorithm is not known.  This takes a single argument,
   * which is the backup ID.
   */
  public static final int MSGID_SCHEMA_RESTORE_UNKNOWN_MAC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 71;



  /**
   * The message ID for the message that will be used if a schema backup has a
   * signature with an unknown or unsupported MAC algorithm.  This takes two
   * arguments, which are the backup ID and the MAC algorithm.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_GET_MAC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 72;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to open the file containing the backup archive.  This takes three
   * arguments, which are the backup ID, the path to the backup file, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_OPEN_BACKUP_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 73;



  /**
   * The message ID for the message that will be used if a schema backup is
   * encrypted but the cipher is not known.  This takes a single argument, which
   * is the backup ID.
   */
  public static final int MSGID_SCHEMA_RESTORE_UNKNOWN_CIPHER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 74;



  /**
   * The message ID for the message that will be used if a schema backup is
   * encrypted with an unknown or unsupported cipher.  This takes two arguments,
   * which are the backup ID and the cipher algorithm.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_GET_CIPHER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 75;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to rename the existing backup directory.  This takes four arguments,
   * which are the backup ID, the path to the current backup directory, the path
   * to which it was to be renamed, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_RENAME_CURRENT_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 76;



  /**
   * The message ID for the message that will be used if an error occurs during
   * the schema restore process but the original schema was restored to its
   * original location.  This takes a single argument, which is the path to the
   * schema directory.
   */
  public static final int MSGID_SCHEMA_RESTORE_RESTORED_OLD_SCHEMA =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 77;



  /**
   * The message ID for the message that will be used if an error occurs while
   * the schema restore process and the original schema files could not be
   * moved back into place.  This takes a single argument, which is the path
   * to the directory containing the original schema files.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_RESTORE_OLD_SCHEMA =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 78;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a new directory to hold the restored schema files.  This
   * takes three arguments, which are the backup ID, the desired path for the
   * schema directory, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_CREATE_SCHEMA_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 79;



  /**
   * The message ID for the message that will be used if an error occurs while
   * the schema restore process but the old schema files were saved in an
   * alternate directory.  This takes a single argument, which is the path
   * to the directory containing the original schema files.
   */
  public static final int MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 80;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the next entry from the schema archive.  This takes three
   * arguments, which are the backup ID, the path to the schema archive, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_GET_ZIP_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 81;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create a schema file from the backup archive.  This takes three
   * arguments, which are the backup ID, the path to the file that could not be
   * created, and a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_CREATE_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 82;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read a schema file from the archive or write it to disk.
   * This takes three arguments, which are the backup ID, the name of the file
   * being processed, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 83;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to close the zip stream used to read from the archive.  This takes
   * three arguments, which are the backup ID, the path to the backup archive,
   * and a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 84;



  /**
   * The message ID for the message that will be used if the unsigned hash of
   * the schema backup matches the expected value.  This does not take any
   * arguments.
   */
  public static final int MSGID_SCHEMA_RESTORE_UNSIGNED_HASH_VALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 85;



  /**
   * The message ID for the message that will be used if the unsigned hash of
   * the schema backup does not match the expected value.  This takes a single
   * argument, which is the backup ID.
   */
  public static final int MSGID_SCHEMA_RESTORE_UNSIGNED_HASH_INVALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 86;



  /**
   * The message ID for the message that will be used if the signed hash of the
   * schema backup matches the expected value.  This does not take any
   * arguments.
   */
  public static final int MSGID_SCHEMA_RESTORE_SIGNED_HASH_VALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 87;



  /**
   * The message ID for the message that will be used if the signed hash of the
   * schema backup does not match the expected value.  This takes a single
   * argument, which is the backup ID.
   */
  public static final int MSGID_SCHEMA_RESTORE_SIGNED_HASH_INVALID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 88;



  /**
   * The message ID for the message that will be used to indicate that the
   * backup verification process completed successfully.  This takes two
   * arguments, which are the backup ID and the path to the backup directory.
   */
  public static final int MSGID_SCHEMA_RESTORE_VERIFY_SUCCESSFUL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 89;



  /**
   * The message ID for the message that will be used to indicate that the
   * backup verification process completed successfully.  This takes two
   * arguments, which are the backup ID and the path to the backup directory.
   */
  public static final int MSGID_SCHEMA_RESTORE_SUCCESSFUL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_NOTICE | 90;



  /**
   * The message ID for the message that will be used if a task entry has an
   * invalid task state.  This takes two arguments, which are the DN of the
   * task entry and the invalid state value.
   */
  public static final int MSGID_TASK_INVALID_STATE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 91;



  /**
   * The message ID for the message that will be used if a task entry has an
   * invalid scheduled start time.  This takes two arguments, which are the
   * provided scheduled start time value and the DN of the task entry.
   */
  public static final int MSGID_TASK_CANNOT_PARSE_SCHEDULED_START_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 92;



  /**
   * The message ID for the message that will be used if a task entry has an
   * invalid actual start time.  This takes two arguments, which are the
   * provided actual start time value and the DN of the task entry.
   */
  public static final int MSGID_TASK_CANNOT_PARSE_ACTUAL_START_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 93;



  /**
   * The message ID for the message that will be used if a task entry has an
   * invalid completion time.  This takes two arguments, which are the
   * provided completion time value and the DN of the task entry.
   */
  public static final int MSGID_TASK_CANNOT_PARSE_COMPLETION_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 94;



  /**
   * The message ID for the message that will be used if a task entry is missing
   * a required attribute.  This takes two arguments, which are the DN of the
   * task entry and the name of the missing attribute.
   */
  public static final int MSGID_TASK_MISSING_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 95;



  /**
   * The message ID for the message that will be used if a task entry contains
   * multiple attributes for a given attribute type.  This takes two arguments,
   * which are the attribute name and the DN of the task entry.
   */
  public static final int MSGID_TASK_MULTIPLE_ATTRS_FOR_TYPE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 96;



  /**
   * The message ID for the message that will be used if a task entry does not
   * have any values for a required attribute.  This takes two arguments, which
   * are the attribute name and the DN of the task entry.
   */
  public static final int MSGID_TASK_NO_VALUES_FOR_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 97;



  /**
   * The message ID for the message that will be used if a task entry contains
   * a single-valued attribute with multiple values.  This takes two arguments,
   * which are the attribute name and the DN of the task entry.
   */
  public static final int MSGID_TASK_MULTIPLE_VALUES_FOR_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 98;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to execute a task.  This takes two arguments, which is the DN of
   * the task entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_TASK_EXECUTE_FAILED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 99;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * does not contain the recurring task ID attribute.  This takes a single
   * argument, which is the name of the recurring task ID attribute.
   */
  public static final int MSGID_RECURRINGTASK_NO_ID_ATTRIBUTE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 100;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * contains multiple attributes with the recurring task ID type.  This takes a
   * single argument, which is the name of the recurring task ID attribute.
   */
  public static final int MSGID_RECURRINGTASK_MULTIPLE_ID_TYPES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 101;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * does not contain any recurring task ID value.  This takes a single
   * argument, which is the name of the recurring task ID attribute.
   */
  public static final int MSGID_RECURRINGTASK_NO_ID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 102;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * contains multiple recurring task ID values.  This takes a single argument,
   * which is the name of the recurring task ID attribute.
   */
  public static final int MSGID_RECURRINGTASK_MULTIPLE_ID_VALUES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 103;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * does not contain the class name attribute.  This takes a single argument,
   * which is the name of the class name attribute.
   */
  public static final int MSGID_RECURRINGTASK_NO_CLASS_ATTRIBUTE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 104;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * contains multiple attributes with the class name type.  This takes a single
   * argument, which is the name of the class name attribute.
   */
  public static final int MSGID_RECURRINGTASK_MULTIPLE_CLASS_TYPES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 105;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * does not contain any class name value.  This takes a single
   * argument, which is the name of the class name attribute.
   */
  public static final int MSGID_RECURRINGTASK_NO_CLASS_VALUES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 106;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * contains multiple class name values.  This takes a single argument, which
   * is the name of the recurring task ID attribute.
   */
  public static final int MSGID_RECURRINGTASK_MULTIPLE_CLASS_VALUES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 107;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for use in a recurring task.  This takes three
   * arguments, which are the name of the class, the name of the attribute that
   * holds the class name, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_RECURRINGTASK_CANNOT_LOAD_CLASS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 108;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a task class.  This takes two arguments, which are
   * the name of the specified class and the name of the class that should be
   * the superclass for all Directory Server tasks.
   */
  public static final int MSGID_RECURRINGTASK_CANNOT_INSTANTIATE_CLASS_AS_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 109;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize a task instance.  This takes two arguments, which are
   * the name of the task class and the error message from the initialization
   * failure.
   */
  public static final int MSGID_RECURRINGTASK_CANNOT_INITIALIZE_INTERNAL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 110;



  /**
   * The message ID for the message that will be used if the configuration entry
   * provided when initializing the task backend is null.  This does not take
   * any arguments.
   */
  public static final int MSGID_TASKBE_CONFIG_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 111;



  /**
   * The message ID for the message that will be used if the configuration entry
   * provided when initializing the task backend does not contain any base DNs.
   * This does not take any arguments.
   */
  public static final int MSGID_TASKBE_NO_BASE_DNS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 112;



  /**
   * The message ID for the message that will be used if the configuration entry
   * provided when initializing the task backend contains multiple base DNs.
   * This does not take any arguments.
   */
  public static final int MSGID_TASKBE_MULTIPLE_BASE_DNS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 113;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the string representation of the recurring tasks parent
   * DN as an actual DN.  This takes two arguments, which are a string
   * representation of the DN to decode and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_TASKBE_CANNOT_DECODE_RECURRING_TASK_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 114;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the string representation of the scheduled tasks parent
   * DN as an actual DN.  This takes two arguments, which are a string
   * representation of the DN to decode and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_TASKBE_CANNOT_DECODE_SCHEDULED_TASK_BASE_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 115;



  /**
   * The message ID for the message that will be used as the description of the
   * task retention time configuration attribute.  It does not take any
   * arguments.
   */
  public static final int MSGID_TASKBE_DESCRIPTION_RETENTION_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 116;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the task retention time.  This takes a single
   * argument, which is a string representation of the stack trace that was
   * caught.
   */
  public static final int MSGID_TASKBE_CANNOT_INITIALIZE_RETENTION_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 117;



  /**
   * The message ID for the message that will be used as the description of the
   * task backing file configuration attribute.  It does not take any arguments.
   */
  public static final int MSGID_TASKBE_DESCRIPTION_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 118;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to initialize the task backing file.  This takes a single
   * argument, which is a string representation of the stack trace that was
   * caught.
   */
  public static final int MSGID_TASKBE_CANNOT_INITIALIZE_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 119;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend but the resulting entry doesn't contain a path
   * for the backing file.  This takes a single argument, which is the name of
   * the attribute that holds the path to the backing file.
   */
  public static final int MSGID_TASKBE_NO_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 120;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend to provide a new backing file path but it
   * references a file that already exists.  This takes a single argument, which
   * is the provided path for the backing file.
   */
  public static final int MSGID_TASKBE_BACKING_FILE_EXISTS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 121;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend to provide a new backing file path but the
   * given path is invalid.  This takes a single argument, which is the provided
   * path for the backing file.
   */
  public static final int MSGID_TASKBE_INVALID_BACKING_FILE_PATH =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 122;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend to provide a new backing file path but the
   * parent directory for that file does not exist.  This takes two arguments,
   * which are the path to the missing parent directory and the backing file.
   */
  public static final int MSGID_TASKBE_BACKING_FILE_MISSING_PARENT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 123;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend to provide a new backing file path but the
   * parent for that file exists but is not a directory.  This takes two
   * arguments, which are the path to the parent and the backing file.
   */
  public static final int MSGID_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 124;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend but there is an error getting the path to the
   * backing file.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_TASKBE_ERROR_GETTING_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 125;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend but the resulting entry doesn't contain a
   * completed task retention time.  This takes a single argument, which is the
   * name of the attribute that holds the retention time.
   */
  public static final int MSGID_TASKBE_NO_RETENTION_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 126;



  /**
   * The message ID for the message that will be used if a configuration change
   * is made to the task backend but there is an error getting the completed
   * task retention time.  This takes a single argument, which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_TASKBE_ERROR_GETTING_RETENTION_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 127;



  /**
   * The message ID for the message that will be used to indicate that the
   * completed task retention time has been updated.  This takes a single
   * argument, which is the new retention time.
   */
  public static final int MSGID_TASKBE_UPDATED_RETENTION_TIME =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 128;



  /**
   * The message ID for the message that will be used to indicate that the task
   * backing file path has been updated.  This takes a single argument, which is
   * the new backing file path.
   */
  public static final int MSGID_TASKBE_UPDATED_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 129;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new entry to the task backend but the new entry is not immediately
   * below the scheduled task base or the recurring task base.  This takes two
   * arguments, which are the DN of the scheduled task base and the DN of the
   * recurring task base.
   */
  public static final int MSGID_TASKBE_ADD_DISALLOWED_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 130;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify DN operation in the task backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_TASKBE_MODIFY_DN_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 131;



  /**
   * The message ID for the message that will be used as the header for the task
   * data backing file to indicate that it should not be directly edited.  This
   * does not take any arguments.
   */
  public static final int MSGID_TASKBE_BACKING_FILE_HEADER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 132;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a recurring task with the same ID as another recurring task.  This
   * takes a single argument, which is the recurring task ID.
   */
  public static final int MSGID_TASKSCHED_DUPLICATE_RECURRING_ID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 133;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a task with the same ID as another task.  This takes a single argument,
   * which is the task ID.
   */
  public static final int MSGID_TASKSCHED_DUPLICATE_TASK_ID =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 134;



  /**
   * The message ID for the message that will be used if a recurring task cannot
   * be found to schedule the next iteration when the previous iteration has
   * completed.  This takes two arguments, which are the task ID of the
   * completed task and the recurring task ID of the recurring task.
   */
  public static final int MSGID_TASKSCHED_CANNOT_FIND_RECURRING_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 135;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to schedule the next iteration of a recurring task.  This takes
   * two arguments, which are the recurring task ID and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_TASKSCHED_ERROR_SCHEDULING_RECURRING_ITERATION =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 136;



  /**
   * The message ID for the message that will be used if a recoverable error
   * occurs while parsing the tasks data backing file.  This takes three
   * arguments, which are the path to the tasks backing file, the line number on
   * which the problem occurred, and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_TASKSCHED_CANNOT_PARSE_ENTRY_RECOVERABLE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 137;



  /**
   * The message ID for the message that will be used if an unrecoverable error
   * occurs while parsing the tasks data backing file.  This takes three
   * arguments, which are the path to the tasks backing file, the line number on
   * which the problem occurred, and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_TASKSCHED_CANNOT_PARSE_ENTRY_FATAL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_FATAL_ERROR | 138;



  /**
   * The message ID for the message that will be used if an entry is read from
   * the backing file that does not have a parent and is not the tasks root
   * entry.  This takes two arguments, which are the DN of the entry read and
   * the DN of the tasks root entry.
   */
  public static final int MSGID_TASKSCHED_ENTRY_HAS_NO_PARENT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 139;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to parse an entry as a recurring task and add it to the
   * scheduler.  This takes two arguments, which are the DN of the entry and
   * a message explaining the problem that occurred.
   */
  public static final int
       MSGID_TASKSCHED_CANNOT_SCHEDULE_RECURRING_TASK_FROM_ENTRY =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 140;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to parse an entry as a task and add it to the scheduler.  This
   * takes two arguments, which are the DN of the entry and a message explaining
   * the problem that occurred.
   */
  public static final int MSGID_TASKSCHED_CANNOT_SCHEDULE_TASK_FROM_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 141;



  /**
   * The message ID for the message that will be used if an entry has a DN that
   * is not valid for a task or recurring task.  This takes two arguments, which
   * are the DN of the entry and the path to the tasks backing file.
   */
  public static final int MSGID_TASKSCHED_INVALID_TASK_ENTRY_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 142;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to read the tasks data backing file.  This takes two arguments,
   * which are the path to the backing file and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_TASKSCHED_ERROR_READING_TASK_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 143;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create the tasks data backing file.  This takes two arguments,
   * which are the path to the tasks backing file and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_TASKSCHED_CANNOT_CREATE_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 144;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * does not contain the class name attribute.  This takes a single argument,
   * which is the name of the class name attribute.
   */
  public static final int MSGID_TASKSCHED_NO_CLASS_ATTRIBUTE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 145;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * contains multiple attributes with the class name type.  This takes a single
   * argument, which is the name of the class name attribute.
   */
  public static final int MSGID_TASKSCHED_MULTIPLE_CLASS_TYPES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 146;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * does not contain any class name value.  This takes a single
   * argument, which is the name of the class name attribute.
   */
  public static final int MSGID_TASKSCHED_NO_CLASS_VALUES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 147;



  /**
   * The message ID for the message that will be used if a recurring task entry
   * contains multiple class name values.  This takes a single argument, which
   * is the name of the recurring task ID attribute.
   */
  public static final int MSGID_TASKSCHED_MULTIPLE_CLASS_VALUES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 148;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to load a class for use in a recurring task.  This takes three
   * arguments, which are the name of the class, the name of the attribute that
   * holds the class name, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_TASKSCHED_CANNOT_LOAD_CLASS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 149;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to instantiate a task class.  This takes two arguments, which are
   * the name of the specified class and the name of the class that should be
   * the superclass for all Directory Server tasks.
   */
  public static final int MSGID_TASKSCHED_CANNOT_INSTANTIATE_CLASS_AS_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 150;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize a task instance.  This takes two arguments, which are
   * the name of the task class and the error message from the initialization
   * failure.
   */
  public static final int MSGID_TASKSCHED_CANNOT_INITIALIZE_INTERNAL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 151;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to rename the current tasks backing file to save it for future
   * reference.  This takes three arguments, which are the path to the current
   * file, the path to the save file, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_TASKSCHED_CANNOT_RENAME_CURRENT_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_WARNING | 152;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to rename the new tasks backing file into place.  This takes three
   * arguments, which are the path to the new backing file, the path to the
   * current backing file, and a string representation of the  exception that
   * was caught.
   */
  public static final int MSGID_TASKSCHED_CANNOT_RENAME_NEW_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 153;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write a new tasks backing file to reflect an updated
   * configuration.  This takes two arguments, which are the path to the new
   * file it was trying to create and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_TASKSCHED_CANNOT_WRITE_BACKING_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 154;



  /**
   * The message ID for the message that will be used if an LDIF import is
   * attempted for the task backend.  This does not take any arguments.
   */
  public static final int MSGID_TASKBE_IMPORT_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 155;



  /**
   * The message ID for the message that will be used as the shutdown message
   * for task threads when the task scheduler is being stopped.
   */
  public static final int MSGID_TASKBE_INTERRUPTED_BY_SHUTDOWN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 156;



  /**
   * The message ID for the message that will be used as the description for the
   * configuration attribute that indicates whether to treat all root DSE
   * attributes as user attributes.  It does not take any arguments.
   */
  public static final int MSGID_ROOTDSE_DESCRIPTION_SHOW_ALL_ATTRIBUTES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 157;



  /**
   * The message ID for the message that will be used if a problem occurs while
   * trying to determine the value of the configuration attribute that controls
   * whether to treat all root DSE attributes as user attributes.  It takes two
   * arguments, which are the name of the attribute and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_ROOTDSE_CANNOT_DETERMINE_ALL_USER_ATTRIBUTES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 158;



  /**
   * The message ID for the message that will be used to indicate that the
   * configuration setting controlling whether root DSE attributes will all be
   * treated as user attributes has been updated.  This takes two arguments,
   * which are the name of the configuration attribute and a string
   * representation of the new value.
   */
  public static final int MSGID_ROOTDSE_UPDATED_SHOW_ALL_ATTRS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 159;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a recurring task when an existing iteration is already scheduled.
   * This takes two arguments, which are the recurring task ID and the scheduled
   * task ID.
   */
  public static final int MSGID_TASKSCHED_REMOVE_RECURRING_EXISTING_ITERATION =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 160;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a pending task when there is no such task.  This takes a single
   * argument, which is the task ID of the task.
   */
  public static final int MSGID_TASKSCHED_REMOVE_PENDING_NO_SUCH_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 161;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a pending task but the specified task is not pending.  This takes a
   * single argument, which is the task ID of the task.
   */
  public static final int MSGID_TASKSCHED_REMOVE_PENDING_NOT_PENDING =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 162;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a completed task, but there is no such task in the completed list.
   * This takes a single argument, which is the task ID for the task.
   */
  public static final int MSGID_TASKSCHED_REMOVE_COMPLETED_NO_SUCH_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 163;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove an entry from the task backend with a DN that is not valid for the
   * tasks backend.  This takes a single argument, which is the provided DN.
   */
  public static final int MSGID_TASKBE_DELETE_INVALID_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 164;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a scheduled task from the server when no such task exists.  This
   * takes a single argument, which is the DN of the task entry.
   */
  public static final int MSGID_TASKBE_DELETE_NO_SUCH_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 165;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a scheduled task that is currently running.  This takes a single
   * argument, which is the DN of the task entry.
   */
  public static final int MSGID_TASKBE_DELETE_RUNNING =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 166;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove a recurring task from the server when no such recurring task exists.
   * This takes a single argument, which is the DN of the recurring task entry.
   */
  public static final int MSGID_TASKBE_DELETE_NO_SUCH_RECURRING_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 167;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a search operation in the tasks backend using an invalid base DN.
   * This takes a single argument, which is the specified base DN.
   */
  public static final int MSGID_TASKBE_SEARCH_INVALID_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 168;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a search operation in the tasks backend using a base DN below the
   * scheduled tasks parent entry but that does not refer to an existing task.
   * This takes a single argument, which is the specified base DN.
   */
  public static final int MSGID_TASKBE_SEARCH_NO_SUCH_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 169;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a search operation in the tasks backend using a base DN below the
   * recurring tasks parent entry but that does not refer to an existing
   * recurring task.  This takes a single argument, which is the specified base
   * DN.
   */
  public static final int MSGID_TASKBE_SEARCH_NO_SUCH_RECURRING_TASK =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 170;



  /**
   * The message ID for the message that will be used if the config entry is
   * null when trying to initialize the backup backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_CONFIG_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 171;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to decode the backup root DN.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_BACKUP_CANNOT_DECODE_BACKUP_ROOT_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 172;



  /**
   * The message ID for the message that will be used as the description for the
   * backup directory list configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_DESCRIPTION_BACKUP_DIR_LIST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 173;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the backup directory list.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_BACKUP_CANNOT_DETERMINE_BACKUP_DIR_LIST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 174;



  /**
   * The message ID for the message that will be used if the backup DN is
   * requested to retrieve a null entry.  This does not take any arguments.
   */
  public static final int MSGID_BACKUP_GET_ENTRY_NULL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 175;



  /**
   * The message ID for the message that will be used if the backup DN is
   * requested to retrieve an entry with a DN that is not valid for entries in
   * the backend.  This takes a single argument, which is the requested DN.
   */
  public static final int MSGID_BACKUP_INVALID_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 176;



  /**
   * The message ID for the message that will be used if a backup directory DN
   * does not specify the directory location.  This takes a single argument,
   * which is the requested DN.
   */
  public static final int MSGID_BACKUP_DN_DOES_NOT_SPECIFY_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 177;



  /**
   * The message ID for the message that will be used if requested backup
   * directory is invalid.  This takes two arguments, which are the DN of the
   * backup directory entry and a string representation of the exception that
   * was caught.
   */
  public static final int MSGID_BACKUP_INVALID_BACKUP_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 178;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to load the backup directory descriptor.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_BACKUP_ERROR_GETTING_BACKUP_DIRECTORY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 179;



  /**
   * The message ID for the message that will be used if a requested backup
   * entry does not include the backup ID in the DN.  This takes a single
   * argument, which is the requested backup entry DN.
   */
  public static final int MSGID_BACKUP_NO_BACKUP_ID_IN_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 180;



  /**
   * The message ID for the message that will be used if a requested backup
   * entry does not have a parent DN.  This takes a single argument, which is
   * the requested backup entry DN.
   */
  public static final int MSGID_BACKUP_NO_BACKUP_PARENT_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 181;



  /**
   * The message ID for the message that will be used if a requested backup
   * entry does not include the backup directory in the DN.  This takes a single
   * argument, which is the requested backup ID.
   */
  public static final int MSGID_BACKUP_NO_BACKUP_DIR_IN_DN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 182;



  /**
   * The message ID for the message that will be used if a requested backup does
   * not exist.  This takes two arguments, which are the backup ID and the
   * backup directory path.
   */
  public static final int MSGID_BACKUP_NO_SUCH_BACKUP =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 183;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an add operation in the backup backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_ADD_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 184;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a delete operation in the backup backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_DELETE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 185;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify operation in the backup backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_MODIFY_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 186;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a modify DN operation in the backup backend.  This does not take
   * any arguments.
   */
  public static final int MSGID_BACKUP_MODIFY_DN_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 187;



  /**
   * The message ID for the message that will be used if a search operation
   * specifies an invalid base DN.  This takes a single argument, which is the
   * requested base DN.
   */
  public static final int MSGID_BACKUP_NO_SUCH_ENTRY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 188;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an LDIF export in the backup backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_EXPORT_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 189;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an LDIF import in the backup backend.  This does not take any
   * arguments.
   */
  public static final int MSGID_BACKUP_IMPORT_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 190;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform a backup or restore operation in the backup backend.  This does not
   * take any arguments.
   */
  public static final int MSGID_BACKUP_BACKUP_AND_RESTORE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 191;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * initialize a memory backend with zero or multiple base DNs.  This does not
   * take any arguments.
   */
  public static final int MSGID_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 192;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry that already exists.  This takes a single argument, which is
   * the DN of the target entry.
   */
  public static final int MSGID_MEMORYBACKEND_ENTRY_ALREADY_EXISTS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 193;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an entry that doesn't belong in the backend.  This takes a single
   * argument, which is the DN of the target entry.
   */
  public static final int MSGID_MEMORYBACKEND_ENTRY_DOESNT_BELONG =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 194;



  /**
   * The message ID for the message that will be used if an entry cannot be
   * added because the parent does not exist.  This takes two arguments, which
   * are the entry DN and the parent DN.
   */
  public static final int MSGID_MEMORYBACKEND_PARENT_DOESNT_EXIST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 195;



  /**
   * The message ID for the message that will be used if an operation targets an
   * entry that doesn't exist.  This takes a single argument, which is the DN of
   * the entry.
   */
  public static final int MSGID_MEMORYBACKEND_ENTRY_DOESNT_EXIST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 196;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * delete an entry that has one or more children.  This takes a single
   * argument, which is the DN of the entry.
   */
  public static final int
       MSGID_MEMORYBACKEND_CANNOT_DELETE_ENTRY_WITH_CHILDREN =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 197;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * perform an unsupported modify DN operation.  This does not take any
   * arguments.
   */
  public static final int MSGID_MEMORYBACKEND_MODDN_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 198;



  /**
   * The message ID for the message that will be used if an error occurs while
   * creating an LDIF writer.  This takes a single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_MEMORYBACKEND_CANNOT_CREATE_LDIF_WRITER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 199;



  /**
   * The message ID for the message that will be used if an error occurs while
   * writing an entry to LDIF.  This takes two arguments, which are the entry DN
   * and a message explaining the problem that occurred.
   */
  public static final int MSGID_MEMORYBACKEND_CANNOT_WRITE_ENTRY_TO_LDIF =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 200;



  /**
   * The message ID for the message that will be used if an error occurs while
   * creating an LDIF reader.  This takes a single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 201;



  /**
   * The message ID for the message that will be used if an error occurs while
   * reading an entry from LDIF.  This takes a single argument, which is a
   * message explaining the problem that occurred.
   */
  public static final int MSGID_MEMORYBACKEND_ERROR_READING_LDIF =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 202;



  /**
   * The message ID for the message that will be used if an error occurs during
   * an LDIF import.  This takes a single argument, which is a message
   * explaining the problem that occurred.
   */
  public static final int MSGID_MEMORYBACKEND_ERROR_DURING_IMPORT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 203;



  /**
   * The message ID for the message that will be used to indicate that backup
   * and restore operations are not supported in the memory-based backend.  This
   * does not take any arguments.
   */
  public static final int MSGID_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 204;



  /**
   * The message ID for the message that will be used to indicate that an entry
   * cannot be renamed if it has children.  This takes a single argument, which
   * is the DN of the target entry.
   */
  public static final int MSGID_MEMORYBACKEND_CANNOT_RENAME_ENRY_WITH_CHILDREN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 205;



  /**
   * The message ID for the message that will be used to indicate that an entry
   * cannot be renamed if it would move to another backend.  This takes a single
   * argument, which is the DN of the target entry.
   */
  public static final int MSGID_MEMORYBACKEND_CANNOT_RENAME_TO_ANOTHER_BACKEND =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 206;



  /**
   * The message ID for the message that will be used to indicate that an entry
   * cannot be renamed because the new parent doesn't exist.  This takes two
   * arguments, which are the current DN and the new parent DN.
   */
  public static final int MSGID_MEMORYBACKEND_RENAME_PARENT_DOESNT_EXIST =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 207;



  /**
   * The message ID for the message that will be used as the description for the
   * configuration attribute that is used to indicate whether all attributes
   * in the subschema entry should be shown even if they are marked operational.
   * This does not take any arguments.
   */
  public static final int MSGID_SCHEMA_DESCRIPTION_SHOW_ALL_ATTRIBUTES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_INFORMATIONAL | 208;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to treat all subschema attributes as user
   * attributes.  This takes two arguments, which are the DN of the
   * configuration entry and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_SCHEMA_CANNOT_DETERMINE_SHOW_ALL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 209;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to register a base DN for use in the server.  This takes two
   * arguments, which are the base DN and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_BACKEND_CANNOT_REGISTER_BASEDN =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_FATAL_ERROR | 210;



  /**
   * The message ID for the message that will be used if a modify operation
   * attempts to delete existing schema elements, which is not currently
   * supported.  This does not take any arguments.
   */
  public static final int MSGID_SCHEMA_DELETE_MODTYPE_NOT_SUPPORTED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 211;



  /**
   * The message ID for the message that will be used if a modify operation
   * attempts to replace or increment schema elements, which is not allowed.
   * This takes a single argument, which is the name of the attempted
   * modification type.
   */
  public static final int MSGID_SCHEMA_INVALID_MODIFICATION_TYPE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 212;



  /**
   * The message ID for the message that will be used if a modify operation
   * attempts to modify an attribute type that cannot be changed.  This takes a
   * single argument, which is the name of the target attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 213;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a new attribute type.  This takes two arguments, which
   * are the attribute type string and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 214;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new attribute type with a name or OID that conflicts with an existing
   * attribute type.  This takes two arguments, which are the name of the new
   * attribute type and a message explaining the problem that occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_ATTRTYPE_ALREADY_EXISTS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 215;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a new objectclass.  This takes two arguments, which
   * are the objectclass string and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 216;



  /**
   * The message ID for the message that will be used if a new objectclass
   * references an undefined superior class.  This takes two arguments, which
   * are the name of the new objectclass and the name of the undefined superior
   * class.
   */
  public static final int MSGID_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_OBJECTCLASS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 217;



  /**
   * The message ID for the message that will be used if a new objectclass
   * references an undefined required attribute.  This takes two arguments,
   * which are the name of the new objectclass and the name of the undefined
   * attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_OC_UNDEFINED_REQUIRED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 218;



  /**
   * The message ID for the message that will be used if a new objectclass
   * references an undefined optional attribute.  This takes two arguments,
   * which are the name of the new objectclass and the name of the undefined
   * attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_OC_UNDEFINED_OPTIONAL_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 219;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new objectclass type with a name or OID that conflicts with an
   * existing objectclass.  This takes two arguments, which are the name of the
   * new objectclass and a message explaining the problem that occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_OBJECTCLASS_ALREADY_EXISTS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 220;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to read the contents of an existing schema file so that it may
   * be updated.  This takes two arguments, which are the path to the schema
   * file and a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_READ_EXISTING_USER_SCHEMA =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 221;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write an updated schema file.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_SCHEMA =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 222;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a new name form.  This takes two arguments, which
   * are the name form string and a message explaining the problem that
   * occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 223;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a new DIT content rule.  This takes two arguments,
   * which are the DIT content rule string and a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DCR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 224;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a new DIT structure rule.  This takes two arguments,
   * which are the DIT structure rule string and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DSR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 225;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to decode a new matching rule use.  This takes two arguments,
   * which are the matching rule use string and a message explaining the problem
   * that occurred.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_DECODE_MR_USE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 226;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * remove all values for a given attribute type.  This takes a single
   * argument, which is the name of that attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DELETE_NO_VALUES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 227;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new attribute type that conflicts with multiple existing attribute
   * types.  This takes three arguments, which are the name or OID of the new
   * attribute type, and the name or OID of the two attribute types that were
   * found to conflict with the new type.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_ATTRTYPE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 228;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new attribute type that references an undefined superior attribute
   * type.  This takes two arguments, which are the name or OID of the new
   * attribute type and the name or OID of the superior attribute type.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_ATTRIBUTE_TYPE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 229;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new objectclas that conflicts with multiple existing objectclasses.
   * This takes three arguments, which are the name or OID of the new
   * objectclass, and the name or OID of the two objectclasses that were found
   * to conflict with the new objectclass.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_OBJECTCLASS =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 230;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new name form that conflicts with multiple existing name forms.  This
   * takes three arguments, which are the name or OID of the new name form, and
   * the name or OID of the two name forms that were found to conflict with the
   * new name form.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_NAME_FORM =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 231;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new name form that references an undefined structural objectclass.
   * This takes two arguments, which are the name or OID of the new name form
   * and the name or OID of the undefined objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_UNDEFINED_STRUCTURAL_OC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 232;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new name form that references an undefined required attribute type.
   * This takes two arguments, which are the name or OID of the new name form
   * and the name or OID of the undefined attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_UNDEFINED_REQUIRED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 233;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new name form that references an undefined optional attribute type.
   * This takes two arguments, which are the name or OID of the new name form
   * and the name or OID of the undefined attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_UNDEFINED_OPTIONAL_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 234;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that conflicts with multiple existing DIT
   * content rules.  This takes three arguments, which are the name of the new
   * DIT content rule, and the names of the two DIT content rules that were
   * found to conflict with the new DIT content rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DCR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 235;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references a structural objectclass which
   * is already referenced by an existing DIT copntent rule.  This takes three
   * arguments, which are the name of the new DIT content rule, the name or OID
   * of the structural objectclass, and the name of the conflicting DIT content
   * rule.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_DCR =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 236;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references a structural objectclass which
   * is not defined in the server schema.  This takes two arguments, which are
   * the name of the new DIT content rule and the name or OID of the undefined
   * objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_STRUCTURAL_OC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 237;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references an auxiliary objectclass which
   * is not defined in the server schema.  This takes two arguments, which are
   * the name of the new DIT content rule and the name or OID of the undefined
   * objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_AUXILIARY_OC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 238;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references a required attribute type
   * which is not defined in the server schema.  This takes two arguments, which
   * are the name of the new DIT content rule and the name or OID of the
   * undefined attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_REQUIRED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 239;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references an optional attribute type
   * which is not defined in the server schema.  This takes two arguments, which
   * are the name of the new DIT content rule and the name or OID of the
   * undefined attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_OPTIONAL_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 240;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references a prohibited attribute type
   * which is not defined in the server schema.  This takes two arguments, which
   * are the name of the new DIT content rule and the name or OID of the
   * undefined attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_PROHIBITED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 241;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT structure rule that conflicts with multiple existing DIT
   * structure rules.  This takes three arguments, which are the name or rule ID
   * of the new DIT structure rule, and the names or rule IDs of the two DIT
   * structure rules that were found to conflict with the new DIT structure
   * rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DSR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 242;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT structure rule that references a name form that is already
   * referenced by another DIT structure rule.  This takes three arguemnts,
   * which are the name or rule ID of the new DIT structure rule, the name or
   * OID of the name form, and the name or rule ID of the conflicting DIT
   * structure rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_NAME_FORM_CONFLICT_FOR_ADD_DSR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 243;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT structure rule that references a name form that is not
   * defined in the server schema.  This takes two arguments, which are the name
   * or rule ID of the new DIT structure rule and the name or OID of the
   * undefined name form.
   */
  public static final int MSGID_SCHEMA_MODIFY_DSR_UNDEFINED_NAME_FORM =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 244;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new matching rule use that conflicts with multiple existing matching
   * rule uses.  This takes three arguments, which are the name of the new
   * matching rule use, and the names of the two matching rule uses that were
   * found to conflict with the new matching rule use.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_MR_USE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 245;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new matching rule use that references a matching rule that is already
   * associated with another matching rule use.  This takes three arguments,
   * which are the name of the new matching rule use, the name or OID of the
   * matching rule, and the name of the conflicting matching rule use.
   */
  public static final int MSGID_SCHEMA_MODIFY_MR_CONFLICT_FOR_ADD_MR_USE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 246;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new matching rule use that references an attribute type that is not
   * defined in the server schema.  This takes two arguments, which are the
   * name of the new matching rule use and the name or OID of the undefined
   * attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_MRU_UNDEFINED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 247;



  /**
   * The message ID for the message that will be used if a circular reference
   * is detected in the superior chain for an attribute type.  This takes a
   * single argument, which is the name or OID of the attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_AT =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 248;



  /**
   * The message ID for the message that will be used if a circular reference
   * is detected in the superior chain for an objectclass.  This takes a single
   * argument, which is the name or OID of the objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_OC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 249;



  /**
   * The message ID for the message that will be used if a circular reference
   * is detected in the superior chain for a DIT structure rule.  This takes a
   * single argument, which is the name or rule ID of the DIT structure rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_DSR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 250;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create copies of the current schema files, but the server was
   * able to properly clean up after itself.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_CLEANED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 251;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to create copies of the current schema files, and the server was
   * not able to properly clean up after itself.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_NOT_CLEANED =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 252;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write the new schema files, but the server was able to properly
   * clean up after itself.  This takes a single argument,  which is a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_RESTORED =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 253;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write the new schema files, and the server was not able to
   * properly clean up after itself.  This takes a single argument, which is a
   * string representation of the exception that was caught.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_NOT_RESTORED =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 254;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an attribute type from the schema fails because there is no such attribute
   * type defined.  This takes a single argument, which is the name or OID of
   * the attribute type to remove.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_ATTRIBUTE_TYPE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 255;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an attribute type from the schema fails because it is the superior type for
   * another attribute type.  This takes two arguments, which are the name or
   * OID of the attribute type to remove, and the name or OID of the subordinate
   * attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_AT_SUPERIOR_TYPE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 256;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an attribute type from the schema fails because the attribute type is
   * referenced by an objectclass.  This takes two arguments, which are the name
   * or OID of the attribute type and the name or OID of the objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_OC =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 257;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an attribute type from the schema fails because the attribute type is
   * referenced by a name form.  This takes two arguments, which are the name or
   * OID of the attribute type and the name or OID of the name form.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_NF =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 258;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an attribute type from the schema fails because the attribute type is
   * referenced by a DIT content rule.  This takes two arguments, which are the
   * name or OID of the attribute type and the name of the DIT content rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_DCR =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 259;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an attribute type from the schema fails because the attribute type is
   * referenced by a matching rule use.  This takes two arguments, which are the
   * name or OID of the attribute type and the name of the matching rule use.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_MR_USE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 260;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an objectclass from the schema fails because there is no such objectclass
   * defined.  This takes a single argument, which is the name or OID of the
   * objectclass to remove.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_OBJECTCLASS =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 261;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an objectclass from the schema fails because it is the superior class for
   * another objectclass.  This takes two arguments, which are the name or OID
   * of the objectclass to remove, and the name or OID of the subordinate
   * objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_OC_SUPERIOR_CLASS =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 262;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an objectclass from the schema fails because the objectclass is referenced
   * by a name form.  This takes two arguments, which are the name or OID of the
   * objectclass and the name or OID of the name form.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_OC_IN_NF =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 263;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * an objectclass from the schema fails because the objectclass is referenced
   * by a DIT content rule.  This takes two arguments, which are the name or OID
   * of the objectclass and the name of the DIT content rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_OC_IN_DCR =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 264;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * a name form from the schema fails because there is no such name form
   * defined.  This takes a single argument, which is the name or OID of the
   * name form to remove.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_NAME_FORM =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 265;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * a name form from the schema fails because the name form is referenced by a
   * DIT structure rule.  This takes two arguments, which are the name or OID
   * of the name form and the name or rule ID of the DIT structure rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NF_IN_DSR =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 266;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * a DIT content rule from the schema fails because there is no such DIT
   * content rule defined.  This takes a single argument, which is the name of
   * the DIT content rule to remove.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_DCR =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 267;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * a DIT structure rule from the schema fails because there is no such DIT
   * structure rule defined.  This takes a single argument, which is the name or
   * rule ID of the DIT structure rule to remove.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_DSR =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 268;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * a DIT structure rule from the schema fails because it is the superior rule
   * for another DIT structure rule.  This takes two arguments, which are the
   * name or rule ID of the DIT structure rule to remove, and the name or rule
   * ID of the subordinate rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_DSR_SUPERIOR_RULE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 269;



  /**
   * The message ID for the message that will be used if an attempt to remove
   * a matching rule use from the schema fails because there is no such matching
   * rule use defined.  This takes a single argument, which is the name of the
   * matching rule use to remove.
   */
  public static final int MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_MR_USE =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 270;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new name form that references an objectclass that is not structural.
   * This takes two arguments, which are the name or OID of the new name form
   * and the name or OID of the objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_OC_NOT_STRUCTURAL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 271;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new DIT content rule that references an objectclass that is not
   * structural.  This takes two arguments, which are the name of the new DIT
   * content rule and the name or OID of the objectclass.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_OC_NOT_STRUCTURAL =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 272;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a new name form that references a structural objectclass which is
   * already referenced by an existing name form.  This takes three arguments,
   * which are the name or OID of the new name form, the name or OID of the
   * structural objectclass, and the name or OID of the conflicting name form.
   */
  public static final int
       MSGID_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_NF =
            CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 273;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an attribute type whose superior type is OBSOLETE.  This takes two
   * arguments, which are the name or OID of the attribute type and the name or
   * OID of the superior type.
   */
  public static final int MSGID_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_ATTRIBUTE_TYPE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 274;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an attribute type with a matching rule that is OBSOLETE.  This takes
   * two arguments, which are the name or OID of the attribute type and the name
   * or OID of the matching rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 275;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an object class whose superior class is OBSOLETE.  This takes two
   * arguments, which are the name or OID of the object class and the name or
   * OID of the superior class.
   */
  public static final int MSGID_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_OBJECTCLASS =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 276;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an object class that requires an OBSOLETE attribute type.  This takes
   * two arguments, which are the name or OID of the object class and the name
   * or OID of the required attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_OC_OBSOLETE_REQUIRED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 277;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add an object class that allows an OBSOLETE attribute type.  This takes
   * two arguments, which are the name or OID of the object class and the name
   * or OID of the optional attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_OC_OBSOLETE_OPTIONAL_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 278;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a name form whose structural object class is marked OBSOLETE.  This
   * takes two arguments, which are the name or OID of the name form and the
   * name or OID of the structural object class.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_OC_OBSOLETE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 279;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a name form that requires an attribute type which is marked OBSOLETE.
   * This takes two arguments, which are the name or OID of the name form and
   * the name or OID of the required attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_OBSOLETE_REQUIRED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 280;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a name form that allows an attribute type which is marked OBSOLETE.
   * This takes two arguments, which are the name or OID of the name form and
   * the  name or OID of the optional attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_NF_OBSOLETE_OPTIONAL_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 281;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule whose structural object class is marked OBSOLETE.
   * This takes two arguments, which are the name of the DIT content rule and
   * the name or OID of the structural object class.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_STRUCTURAL_OC_OBSOLETE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 282;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule with an auxiliary object class that is not declared
   * auxiliary.  This takes two arguments, which are the name of the DIT content
   * rule and the name or OID of the object class.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_OC_NOT_AUXILIARY =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 283;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule with an AUXILIARY object class that is marked
   * OBSOLETE.  This takes two arguments, which are the name of the DIT content
   * rule and the name or OID of the auxiliary object class.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_AUXILIARY_OC_OBSOLETE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 284;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule that requires an attribute type that is marked
   * OBSOLETE.  This takes two arguments, which are the name of the DIT content
   * rule and the name or OID of the attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_REQUIRED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 285;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule that allows an attribute type that is marked
   * OBSOLETE.  This takes two arguments, which are the name of the DIT content
   * rule and the name or OID of the attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_OPTIONAL_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 286;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule that prohibits an attribute type that is marked
   * OBSOLETE.  This takes two arguments, which are the name of the DIT content
   * rule and the name or OID of the attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_PROHIBITED_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 287;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT structure rule whose associated name form is marked OBSOLETE.
   * This takes two arguments, which are the name or rule ID of the DIT
   * structure rule and the name or OID of the name form.
   */
  public static final int MSGID_SCHEMA_MODIFY_DSR_OBSOLETE_NAME_FORM =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 288;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT structure rule with a superior rule that is marked OBSOLETE.
   * This takes two arguments, which are the name or rule ID of the DIT
   * structure rule and the name or rule ID of the superior rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_DSR_OBSOLETE_SUPERIOR_RULE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 289;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a matching rule use with a matching rule that is marked OBSOLETE.  This
   * takes two arguments, which are the name of the matching rule use and the
   * name or OID of the matching rule.
   */
  public static final int MSGID_SCHEMA_MODIFY_MRU_OBSOLETE_MR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 290;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a matching rule use with an attribute type that is marked OBSOLETE.
   * This takes two arguments, which are the name of the matching rule use and
   * the name or OID of the attribute type.
   */
  public static final int MSGID_SCHEMA_MODIFY_MRU_OBSOLETE_ATTR =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 291;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * add a DIT content rule with an auxiliary object class that is declared
   * OBSOLETE.  This takes two arguments, which are the name of the DIT content
   * rule and the name or OID of the object class.
   */
  public static final int MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_AUXILIARY_OC =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 292;



  /**
   * The message ID for the message that will be used if a user attempts to
   * modify the server schema without the appropriate privilege.  This does not
   * take any arguments.
   */
  public static final int MSGID_SCHEMA_MODIFY_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_MILD_ERROR | 293;



  /**
   * The message ID for the message that will be used if the schema backend is
   * unable to find a file containing the concatenated schema definitions.  This
   * takes three arguments, which are the path to the directory in which the
   * file should have been found, the name of the most recent concatenated
   * schema file, and the name of the base concatenated schema file shipped with
   * the server.
   */
  public static final int MSGID_SCHEMA_CANNOT_FIND_CONCAT_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 294;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting determine whether there were any changes made to the server
   * schema while the server was offline.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 295;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write a file containing the concatenated server schema.  This
   * takes two arguments, which are the path to the file being written and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_SCHEMA_CANNOT_WRITE_CONCAT_SCHEMA_FILE =
       CATEGORY_MASK_BACKEND | SEVERITY_MASK_SEVERE_ERROR | 296;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_BACKEND_CANNOT_REMOVE_MULTIBASE_SUB_SUFFIX,
                    "An attempt was made to de-register sub-suffix \"%s\" "+
                    "from the backend with suffix \"%s\".  However, the " +
                    "subordinate backend containing that sub-suffix also " +
                    "contains additional sub-suffixes and may not be " +
                    "de-registered.  It may be possible to remove this " +
                    "sub-suffix by editing the configuration for the " +
                    "subordinate backend with which it is associated");
    registerMessage(MSGID_BACKEND_CANNOT_LOCK_ENTRY,
                    "The Directory Server was unable to obtain a lock on " +
                    "entry %s after multiple attempts.  This could mean that " +
                    "the entry is already locked by a long-running operation " +
                    "or that the entry has previously been locked but was " +
                    "not properly unlocked");
    registerMessage(MSGID_BACKEND_CANNOT_REGISTER_BASEDN,
                    "An error occurred while attempting to register base DN " +
                    "in the Directory Server:  %s");


    registerMessage(MSGID_ROOTDSE_CONFIG_ENTRY_NULL,
                    "An attempt was made to configure the root DSE backend " +
                    "without providing a configuration entry.  This is not " +
                    "allowed");
    registerMessage(MSGID_ROOTDSE_SUBORDINATE_BASE_DESCRIPTION,
                    "Specifies the set of base DNs that will be used for " +
                    "singleLevel, wholeSubtree, and subordinateSubtree " +
                    "searches based at the root DSE.  If this is not " +
                    "provided, then the set of all user-defined suffixes " +
                    "will be used");
    registerMessage(MSGID_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE,
                    "Base DN \"%s\" is configured as one of the subordinate " +
                    "base DNs to use for searches below the root DSE.  " +
                    "However, this base DN is not handled by any suffix " +
                    "registered with the Directory Server and will therefore " +
                    "not be used");
    registerMessage(MSGID_ROOTDSE_SUBORDINATE_BASE_EXCEPTION,
                    "An unexpected problem occurred while trying to " +
                    "determine the set of subordinate base DNs to use for " +
                    "searches below the root DSE:  %s");
    registerMessage(MSGID_ROOTDSE_DESCRIPTION_SHOW_ALL_ATTRIBUTES,
                    "Indicates whether all attributes in the root DSE should " +
                    "be treated like user attributes (and therefore returned " +
                    "to clients by default) regardless of the Directory " +
                    "Server schema configuration");
    registerMessage(MSGID_ROOTDSE_CANNOT_DETERMINE_ALL_USER_ATTRIBUTES,
                    "An error occurred while trying to determine the value " +
                    "of the %s configuration attribute, which controls " +
                    "whether to treat all root DSE attributes like user " +
                    "attributes:  %s.  The attributes in the root DSE will " +
                    "be treated based on their definition in the server " +
                    "schema");
    registerMessage(MSGID_ROOTDSE_GET_ENTRY_NONROOT,
                    "The root DSE backend was asked to retrieve entry with " +
                    "DN \"%s\".  This backend should only be asked to " +
                    "retrieve the root DSE itself.  However, it will check " +
                    "with the defined subordinate backends and see if it " +
                    "can find the requested entry");
    registerMessage(MSGID_ROOTDSE_ADD_NOT_SUPPORTED,
                    "Unwilling to add entry \"%s\" because add operations " +
                    "are not supported in the root DSE backend");
    registerMessage(MSGID_ROOTDSE_DELETE_NOT_SUPPORTED,
                    "Unwilling to remove entry \"%s\" because delete " +
                    "operations are not supported in the root DSE backend");
    registerMessage(MSGID_ROOTDSE_MODIFY_NOT_SUPPORTED,
                    "Unwilling to update entry \"%s\" because modify " +
                    "operations are not supported in the root DSE backend.  " +
                    "If you wish to alter the contents of the root DSE " +
                    "itself, then it may be possible to do so by modifying " +
                    "the \"%s\" entry in the configuration");
    registerMessage(MSGID_ROOTDSE_MODIFY_DN_NOT_SUPPORTED,
                    "Unwilling to rename entry \"%s\" because modify DN " +
                    "operations are not supported in the root DSE backend");
    registerMessage(MSGID_ROOTDSE_INVALID_SEARCH_BASE,
                    "Unwilling to perform a search (connection ID %d, " +
                    "operation ID %d) with a base DN of \"%s\" in the root " +
                    "DSE backend.  The base DN for searches in this backend " +
                    "must be the DN of the root DSE itself");
    registerMessage(MSGID_ROOTDSE_UNEXPECTED_SEARCH_FAILURE,
                    "An unexpected failure occurred while trying to process " +
                    "a search operation (connection ID %d, operation ID %d) " +
                    "in the root DSE backend:  %s");
    registerMessage(MSGID_ROOTDSE_INVALID_SEARCH_SCOPE,
                    "Unable to process the search with connection ID %d and " +
                    "operation ID %d because it had an invalid scope of %s");
    registerMessage(MSGID_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER,
                    "An unexpected error occurred while trying to open the " +
                    "LDIF writer for the root DSE backend:  %s");
    registerMessage(MSGID_ROOTDSE_UNABLE_TO_EXPORT_DSE,
                    "An unexpected error occurred while trying to export the " +
                    "root DSE entry to the specified LDIF target: %s");
    registerMessage(MSGID_ROOTDSE_IMPORT_NOT_SUPPORTED,
                    "The root DSE backend does not support LDIF import " +
                    "operations");
    registerMessage(MSGID_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED,
                    "The root DSE backend does not provide a facility for " +
                    "backup and restore operations.  The contents of the " +
                    "root DSE should be backed up as part of the Directory " +
                    "Server configuration");
    registerMessage(MSGID_ROOTDSE_USING_SUFFIXES_AS_BASE_DNS,
                    "The root DSE configuration has been updated so that it " +
                    "will now use the defined set of Directory Server " +
                    "suffixes when performing searches below the root DSE");
    registerMessage(MSGID_ROOTDSE_USING_NEW_SUBORDINATE_BASE_DNS,
                    "The root DSE configuration has been updated so that it " +
                    "will now use the base DN set %s when performing " +
                    "below the root DSE");
    registerMessage(MSGID_ROOTDSE_UPDATED_SHOW_ALL_ATTRS,
                    "The root DSE configuration has been updated so that " +
                    "configuration attribute %s will now use a value of %s");
    registerMessage(MSGID_ROOTDSE_USING_NEW_USER_ATTRS,
                    "The root DSE configuration has been updated so that it " +
                    "will now use a new set of user-defined attributes");


    registerMessage(MSGID_MONITOR_CONFIG_ENTRY_NULL,
                    "An attempt was made to configure the monitor backend " +
                    "without providing a configuration entry.  This is not " +
                    "allowed, and no monitor information will be available " +
                    "over protocol");
    registerMessage(MSGID_MONITOR_CANNOT_DECODE_MONITOR_ROOT_DN,
                    "An unexpected error occurred while attempting to decode " +
                    DN_MONITOR_ROOT + " as the base DN for the Directory " +
                    "Server monitor information:  %s.  No monitor " +
                    "information will be available over protocol");
    registerMessage(MSGID_MONITOR_ADD_NOT_SUPPORTED,
                    "Unwilling to add entry \"%s\" because add operations " +
                    "are not supported in the monitor backend");
    registerMessage(MSGID_MONITOR_DELETE_NOT_SUPPORTED,
                    "Unwilling to remove entry \"%s\" because delete " +
                    "operations are not supported in the monitor backend");
    registerMessage(MSGID_MONITOR_MODIFY_NOT_SUPPORTED,
                    "Unwilling to update entry \"%s\" because modify " +
                    "operations are not supported in the monitor backend.  " +
                    "If you wish to alter the contents of the base monitor " +
                    "entry itself, then it may be possible to do so by " +
                    "modifying the \"%s\" entry in the configuration");
    registerMessage(MSGID_MONITOR_MODIFY_DN_NOT_SUPPORTED,
                    "Unwilling to rename entry \"%s\" because modify DN " +
                    "operations are not supported in the monitor backend");
    registerMessage(MSGID_MONITOR_UNABLE_TO_EXPORT_BASE,
                    "An error occurred while attempting to export the base " +
                    "monitor entry:  %s");
    registerMessage(MSGID_MONITOR_UNABLE_TO_EXPORT_PROVIDER_ENTRY,
                    "An error occurred while attempting to export the " +
                    "monitor entry for monitor provider %s:  %s");
    registerMessage(MSGID_MONITOR_IMPORT_NOT_SUPPORTED,
                    "The monitor backend does not support LDIF import " +
                    "operations");
    registerMessage(MSGID_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED,
                    "The monitor backend does not provide a facility for " +
                    "backup and restore operations");
    registerMessage(MSGID_MONITOR_USING_NEW_USER_ATTRS,
                    "The monitor configuration has been updated so that it " +
                    "will now use a new set of user-defined attributes");
    registerMessage(MSGID_MONITOR_GET_ENTRY_NULL,
                    "Unable to retrieve the requested entry from the monitor " +
                    "backend because the provided DN was null");
    registerMessage(MSGID_MONITOR_BASE_TOO_DEEP,
                    "Unable to retrieve the requested entry %s from the " +
                    "monitor backend because the DN is too deep.  Monitor " +
                    "entries may not be more than one level below %s");
    registerMessage(MSGID_MONITOR_INVALID_BASE,
                    "Unable to retrieve the requested entry %s from the " +
                    "monitor backend because the DN is not below the monitor " +
                    "base of %s");
    registerMessage(MSGID_MONITOR_MULTIVALUED_RDN,
                    "Unable to retrieve the requested entry %s from the " +
                    "monitor backend because monitor entries may not contain " +
                    "multivalued RDNs");
    registerMessage(MSGID_MONITOR_NO_SUCH_PROVIDER,
                    "Unable to retrieve the requested entry from the monitor " +
                    "backend because there is no monitor provider \"%s\" " +
                    "registered with the Directory Server");
    registerMessage(MSGID_MONITOR_UPTIME,
                    "%d days %d hours %d minutes %d seconds");


    registerMessage(MSGID_SCHEMA_CONFIG_ENTRY_NULL,
                    "An attempt was made to configure the schema backend " +
                    "without providing a configuration entry.  This is not " +
                    "allowed, and no schema information will be available " +
                    "over protocol");
    registerMessage(MSGID_SCHEMA_DESCRIPTION_ENTRY_DN,
                    "Specifies the DN or set of DNs for the entries that may " +
                    "be retrieved in order to retrieve the Directory Server " +
                    "schema information.  Multiple values may be provided " +
                    "if the schema is to be available in multiple " +
                    "locations for compatibility purposes.  If no value is " +
                    "provided, a default of \"" + DN_DEFAULT_SCHEMA_ROOT +
                    "\" will be used");
    registerMessage(MSGID_SCHEMA_DESCRIPTION_SHOW_ALL_ATTRIBUTES,
                    "Indicates whether to treat attributes in the subschema " +
                    "entry as user attributes even if they are marked " +
                    "operational.  This may provide compatibility with some " +
                    "applications that expect schema attributes like " +
                    "attributeType and objectClasses to be included by " +
                    "default even if they are not requested.  Note that the " +
                    "ldapSyntaxes attribute will always be treated as " +
                    "operational in order to avoid problems with attempts to " +
                    "modify the schema over protocol");
    registerMessage(MSGID_SCHEMA_CANNOT_DETERMINE_BASE_DN,
                    "An error occurred while trying to determine the base " +
                    "DNs to use when publishing the Directory Server schema " +
                    "information, as specified in the " + ATTR_SCHEMA_ENTRY_DN +
                    " attribute of configuration entry %s:  %s.  The default " +
                    "schema base DN of " + DN_DEFAULT_SCHEMA_ROOT +
                    " will be used");
    registerMessage(MSGID_SCHEMA_CANNOT_DETERMINE_SHOW_ALL,
                    "An error occurred while trying to determine whether to " +
                    "treat all subschema entry attributes as user attributes " +
                    "regardless of the way they are defined in the schema, " +
                    "as specified in the " + ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES +
                    " attribute of configuration entry %s:  %s.  The default " +
                    "behavior, which is to treat the attribute types as " +
                    "defined in the server schema, will be used");
    registerMessage(MSGID_SCHEMA_CANNOT_FIND_CONCAT_FILE,
                    "Unable to find a file containing concatenated schema " +
                    "element definitions in order to determine if any schema " +
                    "changes were made with the server offline.  The " +
                    "file was expected in the %s directory and should have " +
                    "been named either %s or %s");
    registerMessage(MSGID_SCHEMA_ERROR_DETERMINING_SCHEMA_CHANGES,
                    "An error occurred while attempting to determine whether " +
                    "any schema changes had been made by directly editing " +
                    "the schema files with the server offline:  %s");
    registerMessage(MSGID_SCHEMA_CANNOT_WRITE_CONCAT_SCHEMA_FILE,
                    "An error occurred while attempting to write file %s " +
                    "containing a concatenated list of all server schema " +
                    "elements:  %s.  The server may not be able to " +
                    "accurately identify any schema changes made with the " +
                    "server offline");
    registerMessage(MSGID_SCHEMA_ADD_NOT_SUPPORTED,
                    "Unwilling to add entry \"%s\" because add operations " +
                    "are not supported in the schema backend");
    registerMessage(MSGID_SCHEMA_DELETE_NOT_SUPPORTED,
                    "Unwilling to remove entry \"%s\" because delete " +
                    "operations are not supported in the schema backend");
    registerMessage(MSGID_SCHEMA_MODIFY_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to modify the " +
                    "Directory Server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_NOT_SUPPORTED,
                    "Unwilling to update entry \"%s\" because modify " +
                    "operations are not yet supported in the schema " +
                    "backend.  If you wish to alter the contents of the base " +
                    "schema entry itself, then it may be possible to do so " +
                    "by modifying the \"%s\" entry in the configuration");
    registerMessage(MSGID_SCHEMA_DELETE_MODTYPE_NOT_SUPPORTED,
                    "The schema backend does not currently support removing " +
                    "existing schema elements");
    registerMessage(MSGID_SCHEMA_INVALID_MODIFICATION_TYPE,
                    "The schema backend does not support the %s modification " +
                    "type");
    registerMessage(MSGID_SCHEMA_MODIFY_UNSUPPORTED_ATTRIBUTE_TYPE,
                    "The schema backend does not support the modification of " +
                    "the %s attribute type.  Only attribute types, object " +
                    "classes, name forms, DIT content rules, DIT structure " +
                    "rules, and matching rule uses may be modified");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_DECODE_ATTRTYPE,
                    "An error occurred while attempting to decode the " +
                    "attribute type \"%s\":  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_ATTRTYPE_ALREADY_EXISTS,
                    "Unable to add attribute type  %s to the server schema " +
                    "because there is an existing attribute type with a " +
                    "conflicting name or OID:  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_DECODE_OBJECTCLASS,
                    "An error occurred while attempting to decode the object " +
                    "class \"%s\":  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_DECODE_NAME_FORM,
                    "An error occurred while attempting to decode the name " +
                    "form \"%s\":  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DCR,
                    "An error occurred while attempting to decode the DIT " +
                    "content rule \"%s\":  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_DECODE_DSR,
                    "An error occurred while attempting to decode the DIT " +
                    "structure rule \"%s\":  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_DECODE_MR_USE,
                    "An error occurred while attempting to decode the " +
                    "matching rule use \"%s\":  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_DELETE_NO_VALUES,
                    "The server will not allow removing all values for the " +
                    "%s attribute type in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_OBJECTCLASS,
                    "Unable to add objectclass %s because its superior " +
                    "class of %s is not defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_OC_UNDEFINED_REQUIRED_ATTR,
                    "Unable to add objectclass %s because it requires " +
                    "attribute %s which is not defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_OC_UNDEFINED_OPTIONAL_ATTR,
                    "Unable to add objectclass %s because it allows " +
                    "attribute %s which is not defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_OBJECTCLASS_ALREADY_EXISTS,
                    "Unable to add objectclass %s to the server schema " +
                    "because there is an existing objectclass with a " +
                    "conflicting name or OID:  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_READ_EXISTING_USER_SCHEMA,
                    "An error occurred while attempting to read the contents " +
                    "of schema file %s:  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_SCHEMA,
                    "An error occurred while attepting to write the updated " +
                    "schema:  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_DN_NOT_SUPPORTED,
                    "Unwilling to rename entry \"%s\" because modify DN " +
                    "operations are not supported in the schema backend");
    registerMessage(MSGID_SCHEMA_UNABLE_TO_EXPORT_BASE,
                    "An error occurred while attempting to export the base " +
                    "schema entry:  %s");
    registerMessage(MSGID_SCHEMA_IMPORT_NOT_SUPPORTED,
                    "The schema backend does not support LDIF import " +
                    "operations");
    registerMessage(MSGID_SCHEMA_BACKUP_AND_RESTORE_NOT_SUPPORTED,
                    "The schema backend does not yet provide a facility for " +
                    "backup and restore operations");
    registerMessage(MSGID_SCHEMA_INVALID_BASE,
                    "Unable to retrieve the requested entry %s from the " +
                    "schema backend because the DN is equal to one of the " +
                    "schema entry DNs");
    registerMessage(MSGID_SCHEMA_UNABLE_TO_CREATE_LDIF_WRITER,
                    "An unexpected error occurred while trying to open the " +
                    "LDIF writer for the schema backend:  %s");
    registerMessage(MSGID_SCHEMA_DEREGISTERED_BASE_DN,
                    "Successfully deregistered DN %s so that it will no " +
                    "longer be available as a schema entry DN");
    registerMessage(MSGID_SCHEMA_CANNOT_DEREGISTER_BASE_DN,
                    "An error occurred while trying to deregister %s as a " +
                    "schema entry DN:  %s");
    registerMessage(MSGID_SCHEMA_REGISTERED_BASE_DN,
                    "Successfully registered DN %s as a new schema entry DN");
    registerMessage(MSGID_SCHEMA_CANNOT_REGISTER_BASE_DN,
                    "An error occurred while trying to register %s as a " +
                    "schema entry DN:  %s");
    registerMessage(MSGID_SCHEMA_USING_NEW_USER_ATTRS,
                    "The schema configuration has been updated so that it " +
                    "will now use a new set of user-defined attributes");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_GET_MAC,
                    "An error occurred while attempting to obtain the %s MAC " +
                    "provider to create the signed hash for the backup:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_GET_DIGEST,
                    "An error occurred while attempting to obtain the %s " +
                    "message digest to create the hash for the backup:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_CREATE_ARCHIVE_FILE,
                    "An error occurred while trying to create the schema " +
                    "archive file %s in directory %s:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_GET_CIPHER,
                    "An error occurred while attempting to obtain the %s " +
                    "cipher to use to encrypt the backup:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_ZIP_COMMENT,
                    "%s schema backup %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_LIST_SCHEMA_FILES,
                    "An error occurred while attempting to obtain a list " +
                    "of the files in directory %s to include in the schema " +
                    "backup:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_BACKUP_SCHEMA_FILE,
                    "An error occurred while attempting to back up schema " +
                    "file %s:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_CLOSE_ZIP_STREAM,
                    "An error occurred while trying to close the schema " +
                    "archive file %s in directory %s:  %s");
    registerMessage(MSGID_SCHEMA_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR,
                    "An error occurred while attempting to update the backup " +
                    "descriptor file %s with information about the schema " +
                    "backup:  %s");
    registerMessage(MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_ATTRTYPE,
                    "Unable to add attribute type %s because it conflicts " +
                    "with multiple existing attribute types (%s and " +
                    "%s)");
    registerMessage(MSGID_SCHEMA_MODIFY_UNDEFINED_SUPERIOR_ATTRIBUTE_TYPE,
                    "Unable to add attribute type %s because it references " +
                    "superior attribute type %s which is not defined in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_OBJECTCLASS,
                    "Unable to add objectclass %s because it conflicts with " +
                    "multiple existing objectclasses (%s and %s)");
    registerMessage(MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_NAME_FORM,
                    "Unable to add name form %s because it conflicts with " +
                    "multiple existing name forms (%s and %s)");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_UNDEFINED_STRUCTURAL_OC,
                    "Unable to add name form %s because it references " +
                    "structural objectclass %s which is not defined in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_OC_NOT_STRUCTURAL,
                    "Unable to add name form %s because it references " +
                    "objectclass %s which is defined in the server schema " +
                    "but is not a structural objectclass");
    registerMessage(MSGID_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_NF,
                    "Unable to add name form %s because it references " +
                    "structural objectclass %s which is already associated " +
                    "with another name form %s");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_UNDEFINED_REQUIRED_ATTR,
                    "Unable to add name form %s because it references " +
                    "required attribute type %s which is not defined in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_UNDEFINED_OPTIONAL_ATTR,
                    "Unable to add name form %s because it references " +
                    "optional attribute type %s which is not defined in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DCR,
                    "Unable to add DIT content rule %s because it conflicts " +
                    "with multiple existing DIT content rules (%s and %s)");
    registerMessage(MSGID_SCHEMA_MODIFY_STRUCTURAL_OC_CONFLICT_FOR_ADD_DCR,
                    "Unable to add DIT content rule %s because it " +
                    "references structural objectclass %s which is already " +
                    "associated with another DIT content rule %s");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_STRUCTURAL_OC,
                    "Unable to add DIT content rule %s because it " +
                    "references structural objectclass %s which is not " +
                    "defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_OC_NOT_STRUCTURAL,
                    "Unable to add DIT content rule %s because it " +
                    "references structural objectclass %s which is defined " +
                    "in the server schema but is not structural");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_AUXILIARY_OC,
                    "Unable to add DIT content rule %s because it " +
                    "references auxiliary objectclass %s which is not " +
                    "defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_REQUIRED_ATTR,
                    "Unable to add DIT content rule %s because it " +
                    "references required attribute type %s which is not " +
                    "defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_OPTIONAL_ATTR,
                    "Unable to add DIT content rule %s because it " +
                    "references optional attribute type %s which is not " +
                    "defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_UNDEFINED_PROHIBITED_ATTR,
                    "Unable to add DIT content rule %s because it " +
                    "references prohibited attribute type %s which is not " +
                    "defined in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_DSR,
                    "Unable to add DIT structure rule %s because it " +
                    "conflicts with multiple existing DIT structure rules " +
                    "(%s and %s)");
    registerMessage(MSGID_SCHEMA_MODIFY_NAME_FORM_CONFLICT_FOR_ADD_DSR,
                    "Unable to add DIT structure rule %s because it " +
                    "references name form %s which is already associated " +
                    "with another DIT structure rule %s");
    registerMessage(MSGID_SCHEMA_MODIFY_DSR_UNDEFINED_NAME_FORM,
                    "Unable to add DIT structure rule %s because it " +
                    "references name form %s which is not defined in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_MULTIPLE_CONFLICTS_FOR_ADD_MR_USE,
                    "Unable to add matching rule use %s because it " +
                    "conflicts with multiple existing matching rule uses " +
                    "(%s and %s)");
    registerMessage(MSGID_SCHEMA_MODIFY_MR_CONFLICT_FOR_ADD_MR_USE,
                    "Unable to add matching rule use %s because it " +
                    "references matching rule %s which is already associated " +
                    "with another matching rule use %s");
    registerMessage(MSGID_SCHEMA_MODIFY_MRU_UNDEFINED_ATTR,
                    "Unable to add matching rule use %s because it " +
                    "references attribute type %s which is not defined in " +
                    "the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_AT,
                    "Circular reference detected for attribute type %s in " +
                    "which the superior type chain references the " +
                    "attribute type itself");
    registerMessage(MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_OC,
                    "Circular reference detected for objectclass %s in which " +
                    "the superior class chain references the objectclass " +
                    "itself");
    registerMessage(MSGID_SCHEMA_MODIFY_CIRCULAR_REFERENCE_DSR,
                    "Circular reference detected for DIT structure rule %s " +
                    "in which the superior rule chain references the DIT " +
                    "structure rule itself");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_CLEANED,
                    "An error occurred while attempting to create copies " +
                    "of the existing schema files before applying the " +
                    "updates:  %s.  The server was able to restore the " +
                    "original schema configuration, so no additional " +
                    "cleanup should be required");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_WRITE_ORIG_FILES_NOT_CLEANED,
                    "An error occurred while attempting to create copies " +
                    "of the existing schema files before applying the " +
                    "updates:  %s.  A problem also occurred when attempting " +
                    "to restore the original schema configuration, so the " +
                    "server may be left in an inconsistent state and could " +
                    "require manual cleanup");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_RESTORED,
                    "An error occurred while attempting to write new " +
                    "versions of the server schema files:  %s.   The server " +
                    "was able to restore the original schema configuration, " +
                    "so no additional cleanup should be required");
    registerMessage(MSGID_SCHEMA_MODIFY_CANNOT_WRITE_NEW_FILES_NOT_RESTORED,
                    "An error occrred while attempting to write new " +
                    "versions of the server schema files:  %s.  A problem " +
                    "also occurred when attempting to restore the original " +
                    "schema configuration, so the server may be left in an " +
                    "inconsistent state and could require manual cleanup");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_ATTRIBUTE_TYPE,
                    "Unable to remove attribute type %s from the server " +
                    "schema because no such attribute type is defined");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_AT_SUPERIOR_TYPE,
                    "Unable to remove attribute type %s from the server " +
                    "schema because it is referenced as the superior type " +
                    "for attribute type %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_OC,
                    "Unable to remove attribute type %s from the server " +
                    "schema because it is referenced as a required or " +
                    "optional attribute type in objectclass %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_NF,
                    "Unable to remove attribute type %s from the server " +
                    "schema because it is referenced as a required or " +
                    "optional attribute type in name form %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_DCR,
                    "Unable to remove attribute type %s from the server " +
                    "schema because it is referenced as a required, " +
                    "optional, or prohibited attribute type in DIT content " +
                    "rule %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_AT_IN_MR_USE,
                    "Unable to remove attribute type %s from the server " +
                    "schema because it is referenced by matching rule use %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_OBJECTCLASS,
                    "Unable to remove objectclass %s from the server schema " +
                    "because no such objectclass is defined");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_OC_SUPERIOR_CLASS,
                    "Unable to remove objectclass %s from the server schema " +
                    "because it is referenced as the superior class for " +
                    "objectclass %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_OC_IN_NF,
                    "Unable to remove objectclass %s from the server schema " +
                    "because it is referenced as the structural class for " +
                    "name form %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_OC_IN_DCR,
                    "Unable to remove objectclass %s from the server schema " +
                    "because it is referenced as a structural or auxiliary " +
                    "class for DIT content rule %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_NAME_FORM,
                    "Unable to remove name form %s from the server schema " +
                    "because no such name form is defined");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NF_IN_DSR,
                    "Unable to remove name form %s from the server schema " +
                    "because it is referenced by DIT structure rule %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_DCR,
                    "Unable to remove DIT content rule %s from the server " +
                    "schema because no such DIT content rule is defined");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_DSR,
                    "Unable to remove DIT structure rule %s from the server " +
                    "schema because no such DIT structure rule is defined");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_DSR_SUPERIOR_RULE,
                    "Unable to remove DIT structure rule %s from the server " +
                    "schema because it is referenced as a superior rule for " +
                    "DIT structure rule %s");
    registerMessage(MSGID_SCHEMA_MODIFY_REMOVE_NO_SUCH_MR_USE,
                    "Unable to remove matching rule use %s from the server " +
                    "schema because no such matching rule use is defined");
    registerMessage(MSGID_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_ATTRIBUTE_TYPE,
                    "Unable to add attribute type %s because the superior " +
                    "type %s is marked as OBSOLETE in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_ATTRTYPE_OBSOLETE_MR,
                    "Unable to add attribute type %s because the associated " +
                    "matching rule %s is marked as OBSOLETE in the server " +
                    "schema");
    registerMessage(MSGID_SCHEMA_MODIFY_OBSOLETE_SUPERIOR_OBJECTCLASS,
                    "Unable to add object class %s because the superior " +
                    "class %s is marked as OBSOLETE in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_OC_OBSOLETE_REQUIRED_ATTR,
                    "Unable to add object class %s because required " +
                    "attribute %s is marked as OBSOLETE in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_OC_OBSOLETE_OPTIONAL_ATTR,
                    "Unable to add object class %s because optional " +
                    "attribute %s is marked as OBSOLETE in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_OC_OBSOLETE,
                    "Unable to add name form %s because its structural " +
                    "object class %s is marked as OBSOLETE in the server " +
                    "schema");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_OBSOLETE_REQUIRED_ATTR,
                    "Unable to add name form %s because it requires " +
                    "attribute type %s which is marked as OBSOLETE in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_NF_OBSOLETE_OPTIONAL_ATTR,
                    "Unable to add name form %s because it allows " +
                    "attribute type %s which is marked as OBSOLETE in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_STRUCTURAL_OC_OBSOLETE,
                    "Unable to add DIT content rule %s because its " +
                    "structural object class %s is marked as OBSOLETE in " +
                    "the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_OC_NOT_AUXILIARY,
                    "Unable to add DIT content rule %s because it references " +
                    "auxiliary object class %s which is defined in the " +
                    "server schema but is not an auxiliary class");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_AUXILIARY_OC,
                    "Unable to add DIT content rule %s because it references " +
                    "auxiliary object class %s which is marked as OBSOLETE " +
                    "in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_AUXILIARY_OC_OBSOLETE,
                    "Unable to add DIT content rule %s because it allows " +
                    "auxiliary object class %s which is marked as OBSOLETE " +
                    "in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_REQUIRED_ATTR,
                    "Unable to add DIT content rule %s because it requires " +
                    "attribute type %s which is marked as OBSOLETE in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_OPTIONAL_ATTR,
                    "Unable to add DIT content rule %s because it allows " +
                    "attribute type %s which is marked as OBSOLETE in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DCR_OBSOLETE_PROHIBITED_ATTR,
                    "Unable to add DIT content rule %s because it prohibits " +
                    "attribute type %s which is marked as OBSOLETE in the " +
                    "server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DSR_OBSOLETE_NAME_FORM,
                    "Unable to add DIT structure rule %s because its name " +
                    "form %s is marked OBSOLETE in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_DSR_OBSOLETE_SUPERIOR_RULE,
                    "Unable to add DIT structure rule %s because it " +
                    "references superior rule %s whihc is marked as OBSOLETE " +
                    "in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_MRU_OBSOLETE_MR,
                    "Unable to add matching rule use %s because its matching " +
                    "rule %s is marked OBSOLETE in the server schema");
    registerMessage(MSGID_SCHEMA_MODIFY_MRU_OBSOLETE_ATTR,
                    "Unable to add matching rule use %s because it " +
                    "references attribute type %s which is marked as " +
                    "OBSOLETE in the server schema");


    registerMessage(MSGID_SCHEMA_RESTORE_NO_SUCH_BACKUP,
                    "Unable to restore or verify schema backup %s in " +
                    "directory %s because no such backup exists");
    registerMessage(MSGID_SCHEMA_RESTORE_NO_BACKUP_FILE,
                    "Unable to restore or verify schema backup %s in " +
                    "directory %s because the archive filename could not be " +
                    "determined");
    registerMessage(MSGID_SCHEMA_RESTORE_NO_SUCH_FILE,
                    "Unable to restore or verify schema backup %s because " +
                    "the specified archive file %s does not exist");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_CHECK_FOR_ARCHIVE,
                    "Unable to restore or verify schema backup %s because " +
                    "an error occurred while trying to determine whether " +
                    "backup archive %s exists:  %s");
    registerMessage(MSGID_SCHEMA_RESTORE_UNKNOWN_DIGEST,
                    "Unable to restore or verify schema backup %s because " +
                    "an unsigned hash of this backup is available but the " +
                    "server cannot determine the digest algorithm used to " +
                    "generate this hash");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_GET_DIGEST,
                    "Unable to restore or verify schema backup %s because " +
                    "it has an unsigned hash that uses an unknown or " +
                    "unsupported digest algorithm of %s");
    registerMessage(MSGID_SCHEMA_RESTORE_UNKNOWN_MAC,
                    "Unable to restore or verify schema backup %s because " +
                    "a signed hash of this backup is available but the " +
                    "server cannot determine the MAC algorithm used to " +
                    "generate this hash");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_GET_MAC,
                    "Unable to restore or verify schema backup %s because " +
                    "it has a signed hash that uses an unknown or " +
                    "unsupported MAC algorithm of %s");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_OPEN_BACKUP_FILE,
                    "Unable to restore or verify schema backup %s because " +
                    "an error occurred while attempting to open the backup " +
                    "archive file %s:  %s");
    registerMessage(MSGID_SCHEMA_RESTORE_UNKNOWN_CIPHER,
                    "Unable to restore or verify schema backup %s because " +
                    "it is encrypted but the server cannot determine the " +
                    "cipher used to perform this encryption");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_GET_CIPHER,
                    "Unable to restore or verify schema backup %s because " +
                    "it is encrypted using an unknown or unsupported cipher " +
                    "of %s");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_RENAME_CURRENT_DIRECTORY,
                    "Unable to restore schema backup %s because an error " +
                    "occurred while attempting to rename the current " +
                    "schema directory from %s to %s:  %s");
    registerMessage(MSGID_SCHEMA_RESTORE_RESTORED_OLD_SCHEMA,
                    "An error occurred that prevented the schema backup from " +
                    "being properly restored.  However, the original schema " +
                    "files that were in place before the start of the " +
                    "restore process have been preserved and are now in " +
                    "their original location of %s");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_RESTORE_OLD_SCHEMA,
                    "An error occurred that prevented the schema backup from " +
                    "being properly restored.  The original schema files " +
                    "that were in place before the start of the restore " +
                    "process have been preserved and are contained in the %s " +
                    "directory");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_CREATE_SCHEMA_DIRECTORY,
                    "Unable to restore schema backup %s because an error " +
                    "occurred while attempting to create a new empty " +
                    "directory %s into which the files should be restored:  " +
                    "%s");
    registerMessage(MSGID_SCHEMA_RESTORE_OLD_SCHEMA_SAVED,
                    "An error occurred that prevented the schema backup from " +
                    "being properly restored.  The original schema files " +
                    "that were in place before the start of the restore " +
                    "process have been preserved in the %s directory");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_GET_ZIP_ENTRY,
                    "Unable to restore or verify schema backup %s because " +
                    "an error occurred while trying to read the next entry " +
                    "from the archive file %s:  %s");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_CREATE_FILE,
                    "Unable to restore schema backup %s because an error " +
                    "occurred while trying to recreate file %s:  %s");
    registerMessage(MSGID_SCHEMA_RESTORE_CANNOT_PROCESS_ARCHIVE_FILE,
                    "Unable to restore or verify schema backup %s because " +
                    "an error occurred while processing archived file %s:  " +
                    "%s");
    registerMessage(MSGID_SCHEMA_RESTORE_ERROR_ON_ZIP_STREAM_CLOSE,
                    "Unable to restore or verify schema backup %s because an " +
                    "unexpected error occurred while trying to close the " +
                    "archive file %s:  %s");
    registerMessage(MSGID_SCHEMA_RESTORE_UNSIGNED_HASH_VALID,
                    "The message digest calculated from the backup archive " +
                    "matches the digest stored with the backup information");
    registerMessage(MSGID_SCHEMA_RESTORE_UNSIGNED_HASH_INVALID,
                    "Unable to restore or verify schema backup %s because " +
                    "the message digest calculated from the backup archive " +
                    "does not match the digest stored with the backup " +
                    "information");
    registerMessage(MSGID_SCHEMA_RESTORE_SIGNED_HASH_VALID,
                    "The signed digest calculated from the backup archive " +
                    "matches the signature stored with the backup " +
                    "information");
    registerMessage(MSGID_SCHEMA_RESTORE_SIGNED_HASH_INVALID,
                    "Unable to restore or verify schema backup %s because " +
                    "the signed digest calculated from the backup archive " +
                    "does not match the signature stored with the backup " +
                    "information");
    registerMessage(MSGID_SCHEMA_RESTORE_VERIFY_SUCCESSFUL,
                    "All tests performed on schema backup %s from directory " +
                    "%s show that the archive appears to be valid");
    registerMessage(MSGID_SCHEMA_RESTORE_SUCCESSFUL,
                    "Schema backup %s was successfully restored from the " +
                    "archive in directory %s");


    registerMessage(MSGID_TASK_INVALID_STATE,
                    "The task defined in entry %s is invalid because it has " +
                    "an invalid state %s");
    registerMessage(MSGID_TASK_CANNOT_PARSE_SCHEDULED_START_TIME,
                    "An error occurred while trying to parse the scheduled " +
                    "start time value %s from task entry %s");
    registerMessage(MSGID_TASK_CANNOT_PARSE_ACTUAL_START_TIME,
                    "An error occurred while trying to parse the actual " +
                    "start time value %s from task entry %s");
    registerMessage(MSGID_TASK_CANNOT_PARSE_COMPLETION_TIME,
                    "An error occurred while trying to parse the completion " +
                    "time value %s from task entry %s");
    registerMessage(MSGID_TASK_MISSING_ATTR,
                    "Task entry %s is missing required attribute %s");
    registerMessage(MSGID_TASK_MULTIPLE_ATTRS_FOR_TYPE,
                    "There are multiple instances of attribute %s in task " +
                    "entry %s");
    registerMessage(MSGID_TASK_NO_VALUES_FOR_ATTR,
                    "There are no values for attribute %s in task entry %s");
    registerMessage(MSGID_TASK_MULTIPLE_VALUES_FOR_ATTR,
                    "There are multiple values for attribute %s in task " +
                    "entry %s");
    registerMessage(MSGID_TASK_EXECUTE_FAILED,
                    "An error occurred while executing the task defined in " +
                    "entry %s:  %s");


    registerMessage(MSGID_RECURRINGTASK_NO_ID_ATTRIBUTE,
                    "The provided recurring task entry does not contain " +
                    "attribute %s which is needed to hold the recurring task " +
                    "ID");
    registerMessage(MSGID_RECURRINGTASK_MULTIPLE_ID_TYPES,
                    "The provided recurring task entry contains multiple " +
                    "attributes with type %s, which is used to hold the " +
                    "recurring task ID, but only a single instance is " +
                    "allowed");
    registerMessage(MSGID_RECURRINGTASK_NO_ID,
                    "The provided recurring task entry does not contain any " +
                    "values for the %s attribute, which is used to specify " +
                    "the recurring task ID");
    registerMessage(MSGID_RECURRINGTASK_MULTIPLE_ID_VALUES,
                    "The provided recurring task entry contains multiple " +
                    "values for the %s attribute, which is used to specify " +
                    "the recurring task ID, but only a single value is " +
                    "allowed");
    registerMessage(MSGID_RECURRINGTASK_NO_CLASS_ATTRIBUTE,
                    "The provided recurring task entry does not contain " +
                    "attribute %s which is needed to specify the " +
                    "fully-qualified name of the class providing the task " +
                    "logic");
    registerMessage(MSGID_RECURRINGTASK_MULTIPLE_CLASS_TYPES,
                    "The provided recurring task entry contains multiple " +
                    "attributes with type %s, which is used to hold the " +
                    "task class name, but only a single instance is allowed");
    registerMessage(MSGID_RECURRINGTASK_NO_CLASS_VALUES,
                    "The provided recurring task entry does not contain any " +
                    "values for the %s attribute, which is used to specify " +
                    "the fully-qualified name of the class providing the " +
                    "task logic");
    registerMessage(MSGID_RECURRINGTASK_MULTIPLE_CLASS_VALUES,
                    "The provided recurring task entry contains multiple " +
                    "values for the %s attribute, which is used to specify " +
                    "the task class name, but only a single value is allowed");
    registerMessage(MSGID_RECURRINGTASK_CANNOT_LOAD_CLASS,
                    "An error occurred while attempting to load class %s " +
                    "specified in attribute %s of the provided recurring " +
                    "task entry:  %s.  Does this class exist in the " +
                    "Directory Server classpath?");
    registerMessage(MSGID_RECURRINGTASK_CANNOT_INSTANTIATE_CLASS_AS_TASK,
                    "An error occurred while trying to create an instance " +
                    "of class %s as a Directory Server task.  Is this class " +
                    "a subclass of %s?");
    registerMessage(MSGID_RECURRINGTASK_CANNOT_INITIALIZE_INTERNAL,
                    "An error occurred while attempting to perform internal " +
                    "initialization on an instance of class %s with the " +
                    "information contained in the provided entry:  %s");


    registerMessage(MSGID_TASKBE_CONFIG_ENTRY_NULL,
                    "The configuration entry provided when attempting to " +
                    "initialize the task backend was null");
    registerMessage(MSGID_TASKBE_NO_BASE_DNS,
                    "The task backend configuration entry does not contain " +
                    "any base DNs.  There must be exactly one base DN for " +
                    "task information in the Directory Server");
    registerMessage(MSGID_TASKBE_MULTIPLE_BASE_DNS,
                    "The task backend configuration entry contains multiple " +
                    "base DNs.  There must be exactly one base DN for task " +
                    "information in the Directory Server");
    registerMessage(MSGID_TASKBE_CANNOT_DECODE_RECURRING_TASK_BASE_DN,
                    "An error occurred while attempting to decode recurring " +
                    "task base %s as a DN:  %s");
    registerMessage(MSGID_TASKBE_CANNOT_DECODE_SCHEDULED_TASK_BASE_DN,
                    "An error occurred while attempting to decode scheduled " +
                    "task base %s as a DN:  %s");
    registerMessage(MSGID_TASKBE_DESCRIPTION_RETENTION_TIME,
                    "Specifies the length of time in seconds that task " +
                    "information should be retained after processing on that " +
                    "task has completed.  Once this period has passed, the " +
                    "task information will be automatically removed to " +
                    "conserve memory and disk space");
    registerMessage(MSGID_TASKBE_CANNOT_INITIALIZE_RETENTION_TIME,
                    "An unexpected error occurred while attempting to " +
                    "initialize the task retention time configuration:  %s");
    registerMessage(MSGID_TASKBE_DESCRIPTION_BACKING_FILE,
                    "Specifies the path to the backing file for the task " +
                    "backend.  This LDIF file will hold all the " +
                    "configuration for the defined scheduled tasks and " +
                    "recurring tasks");
    registerMessage(MSGID_TASKBE_CANNOT_INITIALIZE_BACKING_FILE,
                    "An unexpected error occurred while attempting to " +
                    "initialize the task backing file configuration:  %s");
    registerMessage(MSGID_TASKBE_NO_BACKING_FILE,
                    "The updated configuration entry does not have a value " +
                    "for the required %s attribute, which specifies the " +
                    "path to the task data backing file");
    registerMessage(MSGID_TASKBE_BACKING_FILE_EXISTS,
                    "The specified task data backing file %s already exists " +
                    "and the Directory Server will not attempt to overwrite " +
                    "it.  Please delete or rename the existing file before " +
                    "attempting to use that path for the new backing file, " +
                    "or choose a new path");
    registerMessage(MSGID_TASKBE_INVALID_BACKING_FILE_PATH,
                    "The specified path %s for the new task data backing " +
                    "file appears to be an invalid path.  Please choose a " +
                    "new path for the task data backing file");
    registerMessage(MSGID_TASKBE_BACKING_FILE_MISSING_PARENT,
                    "The parent directory %s for the new task data backing " +
                    "file %s does not exist.  Please create this directory " +
                    "before attempting to use this path for the new backing " +
                    "file or choose a new path");
    registerMessage(MSGID_TASKBE_BACKING_FILE_PARENT_NOT_DIRECTORY,
                    "The parent directory %s for the new task data backing " +
                    "file %s exists but is not a directory.  Please choose a " +
                    "new path for the task data backing file");
    registerMessage(MSGID_TASKBE_ERROR_GETTING_BACKING_FILE,
                    "An error occurred while attempting to determine the " +
                    "new path to the task data backing file:  %s");
    registerMessage(MSGID_TASKBE_NO_RETENTION_TIME,
                    "The updated configuration entry does not have a value " +
                    "for the required %s attribute, which specifies the " +
                    "length of time in seconds that information about " +
                    "completed tasks should be retained before they are " +
                    "cleaned up");
    registerMessage(MSGID_TASKBE_ERROR_GETTING_RETENTION_TIME,
                    "An error occurred while attempting to determine the " +
                    "completed task retention time:  %s");
    registerMessage(MSGID_TASKBE_UPDATED_RETENTION_TIME,
                    "The completed task retention time has been updated to " +
                    "%d seconds.  This will take effect immediately");
    registerMessage(MSGID_TASKBE_UPDATED_BACKING_FILE,
                    "The path to the task data backing file has been changed " +
                    "to %s.  A snapshot of the current task configuration " +
                    "has been written to that file and it will continue to " +
                    "be used for future updates");
    registerMessage(MSGID_TASKBE_ADD_DISALLOWED_DN,
                    "New entries in the task backend may only be added " +
                    "immediately below %s for scheduled tasks or immediately " +
                    "below %s for recurring tasks");
    registerMessage(MSGID_TASKBE_DELETE_INVALID_ENTRY,
                    "Unable to remove entry %s from the task backend because " +
                    "its DN is either not appropriate for that backend or it " +
                    "is not below the scheduled or recurring tasks base entry");
    registerMessage(MSGID_TASKBE_DELETE_NO_SUCH_TASK,
                    "Unable to remove entry %s from the task backend because " +
                    "there is no scheduled task associated with that entry " +
                    "DN");
    registerMessage(MSGID_TASKBE_DELETE_RUNNING,
                    "Unable to delete entry %s from the task backend because " +
                    "the associated task is currently running");
    registerMessage(MSGID_TASKBE_DELETE_NO_SUCH_RECURRING_TASK,
                    "Unable to remove entry %s from the task backend because " +
                    "there is no recurring task associated with that entry " +
                    "DN");
    registerMessage(MSGID_TASKBE_MODIFY_DN_NOT_SUPPORTED,
                    "Modify DN operations are not supported in the task " +
                    "backend");
    registerMessage(MSGID_TASKBE_SEARCH_INVALID_BASE,
                    "Unable to process the search operation in the task " +
                    "backend because the provided base DN %s is not valid " +
                    "for entries in the task backend");
    registerMessage(MSGID_TASKBE_SEARCH_NO_SUCH_TASK,
                    "Unable to process the search operation in the task " +
                    "backend because there is no scheduled task associated " +
                    "with the provided search base entry %s");
    registerMessage(MSGID_TASKBE_SEARCH_NO_SUCH_RECURRING_TASK,
                    "Unable to process the search operation in the task " +
                    "backend because there is no recurring task associated " +
                    "with the provided search base entry %s");
    registerMessage(MSGID_TASKBE_BACKING_FILE_HEADER,
                    "This file contains the data used by the Directory " +
                    "Server task scheduler backend.  Do not edit this file " +
                    "directly, as there is a risk that those changes will be " +
                    "lost.  Scheculed and recurring task definitions should " +
                    "only be edited using the administration utilities " +
                    "provided with the Directory Server");
    registerMessage(MSGID_TASKBE_IMPORT_NOT_SUPPORTED,
                    "The task backend does not support LDIF import " +
                    "operations");
    registerMessage(MSGID_TASKBE_INTERRUPTED_BY_SHUTDOWN,
                    "The tasks backend is being shut down");


    registerMessage(MSGID_TASKSCHED_DUPLICATE_RECURRING_ID,
                    "Unable to add recurring task %s to the task scheduler " +
                    "because another recurring task already exists with the " +
                    "same ID");
    registerMessage(MSGID_TASKSCHED_REMOVE_RECURRING_EXISTING_ITERATION,
                    "Unable to remove recurring task %s because there is " +
                    "already a scheduled iteration of that task with ID %s " +
                    "that must be removed first");
    registerMessage(MSGID_TASKSCHED_REMOVE_PENDING_NO_SUCH_TASK,
                    "Unable to remove pending task %s because no such task " +
                    "exists");
    registerMessage(MSGID_TASKSCHED_REMOVE_PENDING_NOT_PENDING,
                    "Unable to remove pending task %s because the task is " +
                    "no longer pending");
    registerMessage(MSGID_TASKSCHED_REMOVE_COMPLETED_NO_SUCH_TASK,
                    "Unable to remove completed task %s because no such " +
                    "task exists in the list of completed tasks");
    registerMessage(MSGID_TASKSCHED_DUPLICATE_TASK_ID,
                    "Unable to schedule task %s because another task already " +
                    "exists with the same ID");
    registerMessage(MSGID_TASKSCHED_CANNOT_FIND_RECURRING_TASK,
                    "Task %s has completed processing and indicates that it " +
                    "is associated with recurring task %s but no recurring " +
                    "task with that ID is currently defined so it is not " +
                    "possible to schedule the next iteration");
    registerMessage(MSGID_TASKSCHED_ERROR_SCHEDULING_RECURRING_ITERATION,
                    "An error occurred while attempting to schedule the next " +
                    "iteration of recurring task %s:  %s");
    registerMessage(MSGID_TASKSCHED_CANNOT_PARSE_ENTRY_RECOVERABLE,
                    "An error occurred while attempting to read an entry " +
                    "from the tasks backing file %s on or near line %d:  " +
                    "%s.  This is not a fatal error, so the task scheduler " +
                    "will attempt to continue parsing the file and schedule " +
                    "any additional tasks that it contains");
    registerMessage(MSGID_TASKSCHED_CANNOT_PARSE_ENTRY_FATAL,
                    "An error occurred while attempting to read an entry " +
                    "from the tasks backing file %s on or near line %d:  " +
                    "%s.  This is an unrecoverable error, and parsing " +
                    "cannot continue");
    registerMessage(MSGID_TASKSCHED_ENTRY_HAS_NO_PARENT,
                    "Entry %s read from the tasks backing file is invalid " +
                    "because it has no parent and does not match the task " +
                    "root DN of %s");
    registerMessage(MSGID_TASKSCHED_CANNOT_SCHEDULE_RECURRING_TASK_FROM_ENTRY,
                    "An error occurred while attempting to parse entry %s " +
                    "as a recurring task and add it to the scheduler:  %s");
    registerMessage(MSGID_TASKSCHED_CANNOT_SCHEDULE_TASK_FROM_ENTRY,
                    "An error occurred while attempting to parse entry %s " +
                    "as a task and add it to the scheduler:  %s");
    registerMessage(MSGID_TASKSCHED_INVALID_TASK_ENTRY_DN,
                    "Entry %s read from the tasks backing file %s has a DN " +
                    "which is not valid for a task or recurring task " +
                    "definition and will be ignored");
    registerMessage(MSGID_TASKSCHED_ERROR_READING_TASK_BACKING_FILE,
                    "An error occurred while attempting to read from the " +
                    "tasks data backing file %s:  %s");
    registerMessage(MSGID_TASKSCHED_CANNOT_CREATE_BACKING_FILE,
                    "An error occurred while attempting to create a new " +
                    "tasks backing file %s for use with the task " +
                    "scheduler:  %s");
    registerMessage(MSGID_TASKSCHED_NO_CLASS_ATTRIBUTE,
                    "The provided task entry does not contain attribute %s " +
                    "which is needed to specify the fully-qualified name of " +
                    "the class providing the task logic");
    registerMessage(MSGID_TASKSCHED_MULTIPLE_CLASS_TYPES,
                    "The provided task entry contains multiple attributes " +
                    "with type %s, which is used to hold the task class " +
                    "name, but only a single instance is allowed");
    registerMessage(MSGID_TASKSCHED_NO_CLASS_VALUES,
                    "The provided task entry does not contain any values for " +
                    "the %s attribute, which is used to specify the " +
                    "fully-qualified name of the class providing the task " +
                    "logic");
    registerMessage(MSGID_TASKSCHED_MULTIPLE_CLASS_VALUES,
                    "The provided task entry contains multiple values for " +
                    "the %s attribute, which is used to specify the task " +
                    "class name, but only a single value is allowed");
    registerMessage(MSGID_TASKSCHED_CANNOT_LOAD_CLASS,
                    "An error occurred while attempting to load class %s " +
                    "specified in attribute %s of the provided task entry:  " +
                    "%s.  Does this class exist in the Directory Server " +
                    "classpath?");
    registerMessage(MSGID_TASKSCHED_CANNOT_INSTANTIATE_CLASS_AS_TASK,
                    "An error occurred while trying to create an instance " +
                    "of class %s as a Directory Server task.  Is this class " +
                    "a subclass of %s?");
    registerMessage(MSGID_TASKSCHED_CANNOT_INITIALIZE_INTERNAL,
                    "An error occurred while attempting to perform internal " +
                    "initialization on an instance of class %s with the " +
                    "information contained in the provided entry:  %s");
    registerMessage(MSGID_TASKSCHED_CANNOT_RENAME_CURRENT_BACKING_FILE,
                    "An error occurred while attempting to rename the " +
                    "current tasks backing file from %s to %s:  %s.  The " +
                    "previous task configuration (which does not reflect the " +
                    "latest update) may be lost");
    registerMessage(MSGID_TASKSCHED_CANNOT_RENAME_NEW_BACKING_FILE,
                    "An error occurred while attempting to rename the " +
                    "new tasks backing file from %s to %s:  %s.  If the " +
                    "Directory Server is restarted, then the task scheduler " +
                    "may not be able to ");
    registerMessage(MSGID_TASKSCHED_CANNOT_WRITE_BACKING_FILE,
                    "An error occurred while attempting to write the new " +
                    "tasks data backing file %s:  %s.  Configuration " +
                    "information reflecting the latest update may be lost");


    registerMessage(MSGID_BACKUP_CONFIG_ENTRY_NULL,
                    "Unable to initialize the backup backend because the " +
                    "provided configuration entry is null");
    registerMessage(MSGID_BACKUP_CANNOT_DECODE_BACKUP_ROOT_DN,
                    "Unable to initialize the backup backend because an " +
                    "error occurred while attempting to decode the base DN " +
                    "for the backend:  %s");
    registerMessage(MSGID_BACKUP_DESCRIPTION_BACKUP_DIR_LIST,
                    "Specifies the set of directories that will be accessed " +
                    "by default for search operations in the backup " +
                    "backend.  Backup directories not in this list may still " +
                    "be accessed by directly specifying the backup directory " +
                    "in the search base DN.  Changes to this configuration " +
                    "attribute will take effect immediately");
    registerMessage(MSGID_BACKUP_CANNOT_DETERMINE_BACKUP_DIR_LIST,
                    "An error occurred while attempting to determine the " +
                    "backup directory list:  %s.  Initialization of the " +
                    "backup backend cannot continue");
    registerMessage(MSGID_BACKUP_GET_ENTRY_NULL,
                    "Unable to retrieve an entry from the backup backend " +
                    "because the requested entry was null");
    registerMessage(MSGID_BACKUP_INVALID_BASE,
                    "Requested entry %s does not exist in the backup backend");
    registerMessage(MSGID_BACKUP_DN_DOES_NOT_SPECIFY_DIRECTORY,
                    "Unable to retrieve entry %s from the backup backend " +
                    "because the requested DN is one level below the " +
                    "base DN but does not specify a backup directory");
    registerMessage(MSGID_BACKUP_INVALID_BACKUP_DIRECTORY,
                    "Unable to retrieve entry %s from the backup backend " +
                    "because the requested backup directory is invalid:  %s");
    registerMessage(MSGID_BACKUP_ERROR_GETTING_BACKUP_DIRECTORY,
                    "An error occurred while attempting to examine the " +
                    "requested backup directory:  %s");
    registerMessage(MSGID_BACKUP_NO_BACKUP_ID_IN_DN,
                    "Unable to retrieve entry %s from the backup backend " +
                    "because the requested DN is two levels below the " +
                    "base DN but does not specify a backup ID");
    registerMessage(MSGID_BACKUP_NO_BACKUP_PARENT_DN,
                    "Unable to retrieve entry %s from the backup backend " +
                    "because it does not have a parent");
    registerMessage(MSGID_BACKUP_NO_BACKUP_DIR_IN_DN,
                    "Unable to retrieve entry %s from the backup backend " +
                    "because the DN does not contain the backup directory " +
                    "in which the requested backup should reside");
    registerMessage(MSGID_BACKUP_NO_SUCH_BACKUP,
                    "Backup %s does not exist in backup directory %s");
    registerMessage(MSGID_BACKUP_ADD_NOT_SUPPORTED,
                    "Add operations are not supported in the backup backend");
    registerMessage(MSGID_BACKUP_DELETE_NOT_SUPPORTED,
                    "Delete operations are not supported in the backup " +
                    "backend");
    registerMessage(MSGID_BACKUP_MODIFY_NOT_SUPPORTED,
                    "Modify operations are not supported in the backup " +
                    "backend");
    registerMessage(MSGID_BACKUP_MODIFY_DN_NOT_SUPPORTED,
                    "Modify DN operations are not supported in the backup " +
                    "backend");
    registerMessage(MSGID_BACKUP_NO_SUCH_ENTRY,
                    "The requested entry %s does not exist in the backup " +
                    "backend");
    registerMessage(MSGID_BACKUP_EXPORT_NOT_SUPPORTED,
                    "LDIF export operations are not supported in the backup " +
                    "backend");
    registerMessage(MSGID_BACKUP_IMPORT_NOT_SUPPORTED,
                    "LDIF import operations are not supported in the backup " +
                    "backend");
    registerMessage(MSGID_BACKUP_BACKUP_AND_RESTORE_NOT_SUPPORTED,
                    "Backup and restore operations are not supported in " +
                    "the backup backend");


    registerMessage(MSGID_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE,
                    "Exactly one base DN must be provided for use with the " +
                    "memory-based backend");
    registerMessage(MSGID_MEMORYBACKEND_ENTRY_ALREADY_EXISTS,
                    "Entry %s already exists in the memory-based backend");
    registerMessage(MSGID_MEMORYBACKEND_ENTRY_DOESNT_BELONG,
                    "Entry %s does not belong in the memory-based backend");
    registerMessage(MSGID_MEMORYBACKEND_PARENT_DOESNT_EXIST,
                    "Unable to add entry %s because its parent entry %s does " +
                    "not exist in the memory-based backend");
    registerMessage(MSGID_MEMORYBACKEND_ENTRY_DOESNT_EXIST,
                    "Entry %s does not exist in the memory-based backend");
    registerMessage(MSGID_MEMORYBACKEND_CANNOT_DELETE_ENTRY_WITH_CHILDREN,
                    "Cannot delete entry %s because it has one or more " +
                    "subordinate entries");
    registerMessage(MSGID_MEMORYBACKEND_MODDN_NOT_SUPPORTED,
                    "Modify DN operations are not supported in the " +
                    "memory-based backend");
    registerMessage(MSGID_MEMORYBACKEND_CANNOT_CREATE_LDIF_WRITER,
                    "Unable to create an LDIF writer:  %s");
    registerMessage(MSGID_MEMORYBACKEND_CANNOT_WRITE_ENTRY_TO_LDIF,
                    "Cannot write entry %s to LDIF:  %s");
    registerMessage(MSGID_MEMORYBACKEND_CANNOT_CREATE_LDIF_READER,
                    "Unable to create an LDIF reader:  %s");
    registerMessage(MSGID_MEMORYBACKEND_ERROR_READING_LDIF,
                    "An unrecoverable error occurred while reading from " +
                    "LDIF:  %s");
    registerMessage(MSGID_MEMORYBACKEND_ERROR_DURING_IMPORT,
                    "An unexpected error occurred while processing the " +
                    "import:  %s");
    registerMessage(MSGID_MEMORYBACKEND_BACKUP_RESTORE_NOT_SUPPORTED,
                    "The memory-based backend does not support backup or " +
                    "restore operations");
    registerMessage(MSGID_MEMORYBACKEND_CANNOT_RENAME_ENRY_WITH_CHILDREN,
                    "Cannot rename entry %s because it has one or more " +
                    "subordinate entries");
    registerMessage(MSGID_MEMORYBACKEND_CANNOT_RENAME_TO_ANOTHER_BACKEND,
                    "Cannot rename entry %s because the target entry is in a " +
                    "different backend");
    registerMessage(MSGID_MEMORYBACKEND_RENAME_PARENT_DOESNT_EXIST,
                    "Cannot rename entry %s because the new parent entry %s " +
                    "doesn't exist");
  }
}

