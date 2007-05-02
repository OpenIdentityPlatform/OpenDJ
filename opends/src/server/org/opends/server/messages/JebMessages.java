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
 * messages associated with the JE Backend.
 */
public class JebMessages
{
  /**
   * The message ID of an error indicating that the Directory Server has asked a
   * JE backend instance to process an operation on an entry that is not in the
   * scope of that backend instance.  This message takes one string argument
   * which is the DN of the entry.
   */
  public static final int MSGID_JEB_INCORRECT_ROUTING =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 1;

  /**
   * The message ID of an error indicating that the JE backend database files
   * could not be opened.  This message takes one string argument which is the
   * error message provided by the JE library.
   */
  public static final int MSGID_JEB_OPEN_DATABASE_FAIL =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 2;

  /**
   * The message ID of an error indicating that the JE backend environment
   * could not be opened.  This message takes one string argument which is the
   * error message provided by the JE library.
   */
  public static final int MSGID_JEB_OPEN_ENV_FAIL =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 3;

  /**
   * The message ID of an error indicating that the current highest entry ID
   * in the database could not be determined.  This message takes no arguments.
   */
  public static final int MSGID_JEB_HIGHEST_ID_FAIL =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 5;

  /**
   * The message ID of an error indicating that the requested operation is not
   * supported by the JE backend implementation.  This message takes no
   * arguments.
   */
  public static final int MSGID_JEB_FUNCTION_NOT_SUPPORTED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_WARNING | 6;

  /**
   * The message ID of an error indicating that the backend database directory
   * could not be created on the file system.  This message takes one string
   * argument which is the error message provided by the system.
   */
  public static final int MSGID_JEB_CREATE_FAIL =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 7;

  /**
   * The message ID of an error indicating that the backend database directory
   * and files could not be removed.  This message takes one string argument
   * which is the error message provided by the system.
   */
  public static final int MSGID_JEB_REMOVE_FAIL =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 8;

  /**
   * The message ID of an error indicating that the backend database directory
   * is not a valid file system directory.  This message takes one string
   * argument which is the backend database directory pathname.
   */
  public static final int MSGID_JEB_DIRECTORY_INVALID =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 9;

  /**
   * The message ID of an error indicating that the DN database does not contain
   * a record for an entry whose DN it is expected to contain.  This message
   * takes one string argument which is the entry DN.
   */
  public static final int MSGID_JEB_MISSING_DN2ID_RECORD =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 10;

  /**
   * The message ID of an error indicating that the entry database does not
   * contain a record for an entry ID referenced by the DN database.  This
   * message takes one string argument which is the string representation of the
   * entry ID.
   */
  public static final int MSGID_JEB_MISSING_ID2ENTRY_RECORD =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 11;

  /**
   * The message ID of an error indicating that the entry database contains a
   * record that is not valid.  This message takes one string argument which is
   * the string representation of the entry ID forming the key to the record.
   */
  public static final int MSGID_JEB_ENTRY_DATABASE_CORRUPT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 12;

  /**
   * The message ID of an error indicating that an exception was raised by the
   * JE library while accessing the database.  This message takes one string
   * argument which is the error message provided by the JE library.
   */
  public static final int MSGID_JEB_DATABASE_EXCEPTION =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 14;

  /**
   * The message ID used to describe the attribute which configures
   * the attribute type of an attribute index.
   */
  public static final int MSGID_CONFIG_DESCRIPTION_INDEX_ATTRIBUTE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 16;


  /**
   * The message ID used to describe the attribute which configures
   * the type of indexing for an attribute index.
   */
  public static final int MSGID_CONFIG_DESCRIPTION_INDEX_TYPE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 17;


  /**
   * The message ID used to describe the attribute which configures
   * the entry limit for an attribute index.
   */
  public static final int MSGID_CONFIG_DESCRIPTION_INDEX_ENTRY_LIMIT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 18;


  /**
   * The message ID used to describe the attribute which configures
   * the substring length for an attribute index.
   */
  public static final int MSGID_CONFIG_DESCRIPTION_INDEX_SUBSTRING_LENGTH =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 25;


  /**
   * The message ID of an error indicating that the requested type of index
   * cannot be configured for the given attribute type because the attribute
   * definition does not provide an appropriate matching rule.  This message
   * takes two string arguments, the first argument is the attribute type name
   * and the second argument is the type of indexing requested.
   */
  public static final int MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 26;

  /**
   * The message ID of an error indicating that an unchecked exception was
   * raised during a database transaction.  This message takes no arguments.
   */
  public static final int MSGID_JEB_UNCHECKED_EXCEPTION =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 28;

  /**
   * The message ID of an informational message indicating that a forced
   * cleaning of the database log files has started.  This message takes two
   * arguments, the first argument is the current number of log files in the
   * database and the second argument is the database directory pathname.
   */
  public static final int MSGID_JEB_CLEAN_DATABASE_START =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 29;

  /**
   * The message ID of an informational message indicating that some log files
   * have been marked for cleaning during a forced cleaning of the database log
   * files.  This message takes one argument which is the number of log files
   * marked for cleaning.
   */
  public static final int MSGID_JEB_CLEAN_DATABASE_MARKED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 30;

  /**
   * The message ID of an informational message indicating that a forced
   * cleaning of the database log files has finished.  This message takes one
   * argument which is the current number of log files in the database.
   */
  public static final int MSGID_JEB_CLEAN_DATABASE_FINISH =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 31;

  /**
   * The message ID of an informational message indicating that the
   * administrative limit on the number of entries that may be deleted during
   * a subtree delete operation has been exceeded.  This message takes one
   * argument which is the number of entries that were deleted.
   */
  public static final int MSGID_JEB_SUBTREE_DELETE_SIZE_LIMIT_EXCEEDED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 32;

  /**
   * The message ID of an informational message indicating how many entries were
   * deleted by a subtree delete operation.  This message takes one argument
   * which is the number of entries that were deleted.
   */
  public static final int MSGID_JEB_DELETED_ENTRY_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 33;

  /**
   * The message ID of an error indicating that the JE backend configuration
   * contains more than one configuration entry of a given object class where
   * only one entry of that object class is allowed.  This message takes two
   * string arguments, the first argument is the DN of the configuration entry
   * that will be ignored and the second argument is the configuration entry
   * object class.
   */
  public static final int MSGID_JEB_DUPLICATE_CONFIG_ENTRY =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 36;


  /**
   * The message ID of an error indicating that the JE backend configuration
   * contains a configuration entry that is not recognized.  This message takes
   * one string argument which is the DN of the configuration entry that will
   * be ignored.
   */
  public static final int MSGID_JEB_CONFIG_ENTRY_NOT_RECOGNIZED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 37;


  /**
   * The message ID of an error indicating that a configuration entry for an
   * attribute index references an attribute type that is not known by the
   * Directory Server.  This message takes two string arguments, the first
   * argument is the DN of the configuration entry that will be ignored and the
   * second argument is the unknown attribute type name.
   */
  public static final int MSGID_JEB_INDEX_ATTRIBUTE_TYPE_NOT_FOUND =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 38;


  /**
   * The message ID of an error indicating that a configuration entry for an
   * attribute index references an attribute type that has already been
   * referenced in a different configuration entry.  This message takes two
   * arguments, the first argument is the DN of the configuration entry that
   * will be ignored and the second argument is the attribute type name.
   */
  public static final int MSGID_JEB_DUPLICATE_INDEX_CONFIG =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 39;


  /**
   * The message ID of an error indicating that an I/O error occurred during
   * a backend import or export operation.  This message takes one string
   * argument which is the error message provided by the system.
   */
  public static final int MSGID_JEB_IO_ERROR =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 40;


/*
  public static final int MSGID_JEB_INDEX_THREAD_EXCEPTION =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 41;
*/


  /**
   * The message ID of an informational message indicating that a JE backend
   * instance has started, and providing the current number of entries
   * stored in the backend.  This message takes one argument which is the
   * current number of entries stored in the backend.
   */
  public static final int MSGID_JEB_BACKEND_STARTED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 42;


  /**
   * The message ID of an error to be written to an import rejects file,
   * indicating that the parent entry of an entry to be imported does not exist.
   * This message takes one string argument which is the DN of the parent entry.
   */
  public static final int MSGID_JEB_IMPORT_PARENT_NOT_FOUND =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 43;


  /**
   * The message ID of an error to be written to an import rejects file,
   * indicating that the entry to be imported already exists, and the import
   * options do not allow the entry to be replaced.
   */
  public static final int MSGID_JEB_IMPORT_ENTRY_EXISTS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_WARNING | 44;


  /**
   * The message ID of an error indicating that there is no attribute index
   * configured for an attribute type that was provided to an index
   * verification job.  This message takes one string argument which is the
   * attribute type name.
   */
  public static final int MSGID_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 45;


  /**
   * The message ID of an error indicating that the base entry of a search
   * operation does not exist.  This message takes one string argument which is
   * the DN of the search base entry.
   */
  public static final int MSGID_JEB_SEARCH_NO_SUCH_OBJECT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 46;


  /**
   * The message ID of an error indicating that the entry provided in an add
   * operation could not be added because its parent entry does not exist.  This
   * message takes one string argument which is the DN of the entry to be
   * added.
   */
  public static final int MSGID_JEB_ADD_NO_SUCH_OBJECT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 47;


  /**
   * The message ID of an error indicating that the entry provided in a delete
   * operation does not exist.  This message takes one string argument which is
   * the DN of the entry to be deleted.
   */
  public static final int MSGID_JEB_DELETE_NO_SUCH_OBJECT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 48;


  /**
   * The message ID of an error indicating that the entry provided in a modify
   * operation does not exist.  This message takes one string argument which is
   * the DN of the entry to be modified.
   */
  public static final int MSGID_JEB_MODIFY_NO_SUCH_OBJECT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 49;


  /**
   * The message ID of an error indicating that the entry provided in a modify
   * DN operation does not exist.  This message takes one string argument which
   * is the DN of the entry to be renamed.
   */
  public static final int MSGID_JEB_MODIFYDN_NO_SUCH_OBJECT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 50;


  /**
   * The message ID of an error indicating that the entry provided in an add
   * operation already exists.  This message takes one string argument which is
   * the DN of the entry to be added.
   */
  public static final int MSGID_JEB_ADD_ENTRY_ALREADY_EXISTS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 51;


  /**
   * The message ID of an error indicating that the entry provided in a delete
   * operation could not be deleted because it is not a leaf entry, and the
   * subtree delete control was not specified.  This message takes one argument
   * which is the DN of the entry to be deleted.
   */
  public static final int MSGID_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 52;


  /**
   * The message ID of an error indicating that the modify DN operation could
   * not be performed because an entry already exists with the same DN as that
   * of the renamed entry.  This message takes one string argument which is the
   * DN of the existing entry.
   */
  public static final int MSGID_JEB_MODIFYDN_ALREADY_EXISTS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 53;


  /**
   * The message ID of an error indicating that the modify DN operation could
   * not be performed because the specified new superior entry does not exist.
   * This message takes one string argument which is the DN of the new
   * superior entry.
   */
  public static final int MSGID_JEB_NEW_SUPERIOR_NO_SUCH_OBJECT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 54;

  /**
   * The message ID used to log the size of the database cache after preloading.
   */
  public static final int MSGID_JEB_CACHE_SIZE_AFTER_PRELOAD =
       CATEGORY_MASK_JEB | SEVERITY_MASK_NOTICE | 61;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the MAC provider for the backend backup.  This takes
   * two arguments, which are the name of the desired MAC algorithm and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_GET_MAC =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the message digest for the backend backup.  This takes
   * two arguments, which are the name of the desired digest algorithm and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_GET_DIGEST =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 64;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to create the archive file for a backend backup.  This takes
   * three arguments, which are the name of the archive file, the path to the
   * archive directory, and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_CREATE_ARCHIVE_FILE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 65;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain the cipher for the backend backup.  This takes two
   * arguments, which are the name of the desired cipher algorithm and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_GET_CIPHER =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 66;



  /**
   * The message ID for the message that will be used for the message containing
   * the comment to include in the backend archive zip.  This takes three
   * arguments, which are the Directory Server product name, the backup ID,
   * and the backend ID.
   */
  public static final int MSGID_JEB_BACKUP_ZIP_COMMENT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 67;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to obtain a list of the log files to include in the backup.
   * This takes two arguments, which are the path to the directory containing
   * the log files and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_LIST_LOG_FILES =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 68;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to write a file to the archive file.  This takes two
   * arguments, which are the name of the file and a string representation of
   * the exception that was caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_WRITE_ARCHIVE_FILE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 69;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to close the output stream for the backend archive.  This takes
   * three arguments, which are the name of the backend archive file, the path
   * to the directory containing that file, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_CLOSE_ZIP_STREAM =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 70;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the backup descriptor with information about the
   * backend backup.  This takes two arguments, which are the path to the backup
   * descriptor file and a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 71;



  /**
   * The message ID for the message that will be used if an error occurs while
   * verifying the unsigned hash of a backup.  This takes one argument, which
   * is the backup ID containing the hash verification error.
   */
  public static final int MSGID_JEB_BACKUP_UNSIGNED_HASH_ERROR =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 72;



  /**
   * The message ID for the message that will be used if an error occurs while
   * verifying the signed hash of a backup.  This takes one argument, which
   * is the backup ID containing the signed hash verification error.
   */
  public static final int MSGID_JEB_BACKUP_SIGNED_HASH_ERROR =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 73;



  /**
   * The message ID for the message that will be used if an incremental
   * backup is attempted when there has been no previous backup.
   */
  public static final int MSGID_JEB_INCR_BACKUP_REQUIRES_FULL =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 74;



  /**
   * The message ID for the message that will be used if the directory
   * containing the files restored from a backup could not be renamed
   * to the current backend directory.  This takes two arguments,
   * the path of the restored directory and the path of the backend
   * directory.
   */
  public static final int MSGID_JEB_CANNOT_RENAME_RESTORE_DIRECTORY =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 75;



  /**
   * The message ID for the message that will be used if an incremental
   * backup is attempted and the base backup could not be determined.  This
   * takes one argument which is a string representation of the list
   * of IDs of suitable base backups.
   */
  public static final int MSGID_JEB_INCR_BACKUP_FROM_WRONG_BASE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 76;



  /**
   * The message ID for the message that will be used if a backup tag
   * file could not be created in the backend database directory.
   * This takes two arguments, the name of the file and the directory.
   */
  public static final int MSGID_JEB_CANNOT_CREATE_BACKUP_TAG_FILE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 77;



  /**
   * The message ID for the message that will be used if an error occurs while
   * restoring a backup. This takes two arguments, the backup ID, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_JEB_BACKUP_CANNOT_RESTORE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 78;



  /**
   * The message ID for the message that will be used if backup information
   * for a needed backup ID cannot be found in the backup directory.
   * This takes two arguments, the backup directory, and the required backup ID.
   */
  public static final int MSGID_JEB_BACKUP_MISSING_BACKUPID =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 79;



  /**
   * The message ID used to log an informational message in the backup process.
   * This message takes one argument, the name of a file that was not changed
   * since the previous backup.
   */
  public static final int MSGID_JEB_BACKUP_FILE_UNCHANGED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 82;



  /**
   * The message ID used to log an informational message in the backup process.
   * This message takes one argument, the number of additional log files
   * to be included in the backup due to cleaner activity.
   */
  public static final int MSGID_JEB_BACKUP_CLEANER_ACTIVITY =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 83;



  /**
   * The message ID used to log an informational message in the backup process.
   * This message takes one argument, the name of an archived file being
   * processed during a restore in verify-only mode.
   */
  public static final int MSGID_JEB_BACKUP_VERIFY_FILE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 84;



  /**
   * The message ID used to log an informational message in the backup process.
   * This message takes two arguments, the name of an archived file being
   * restored, and its size in bytes.
   */
  public static final int MSGID_JEB_BACKUP_RESTORED_FILE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 85;



  /**
   * The message ID used to log an informational message in the backup process.
   * This message takes one argument, the name of a file being archived.
   */
  public static final int MSGID_JEB_BACKUP_ARCHIVED_FILE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 86;



  /**
   * The message ID used to log an informational message in the export process.
   * This message takes four arguments, the total number of entries exported,
   * the number of entries skipped, the total time in seconds taken by the
   * export process, and the floating point average number of entries
   * exported per second.
   */
  public static final int MSGID_JEB_EXPORT_FINAL_STATUS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 87;



  /**
   * The message ID used to log an informational message in the export process.
   * This message takes three arguments, the number of entries exported so far,
   * the number of entries skipped so far, and the floating point number of
   * entries exported per second since the previous progress report.
   */
  public static final int MSGID_JEB_EXPORT_PROGRESS_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 88;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the configured import thread count
   * which will be used by this import process.
   */
  public static final int MSGID_JEB_IMPORT_THREAD_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 89;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the size in bytes of the buffer allocated
   * to each import thread.
   */
  public static final int MSGID_JEB_IMPORT_BUFFER_SIZE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 90;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the number of seconds it took to process
   * the LDIF file.
   */
  public static final int MSGID_JEB_IMPORT_LDIF_PROCESSING_TIME =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 91;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the number of seconds it took to build
   * the indexes.
   */
  public static final int MSGID_JEB_IMPORT_INDEX_PROCESSING_TIME =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 92;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes no arguments.
   */
  public static final int MSGID_JEB_IMPORT_CLOSING_DATABASE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 93;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes four arguments, the total number of entries imported,
   * the number of entries rejected, the total time in seconds taken by the
   * import process, and the floating point overall average number of entries
   * imported per second.
   */
  public static final int MSGID_JEB_IMPORT_FINAL_STATUS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 94;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the number of index values that
   * exceeded the entry limit.
   */
  public static final int MSGID_JEB_IMPORT_ENTRY_LIMIT_EXCEEDED_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 95;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes three arguments, the number of entries imported so far,
   * the number of entries rejected so far, and the floating point number of
   * entries imported per second since the previous progress report.
   */
  public static final int MSGID_JEB_IMPORT_PROGRESS_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 96;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes two arguments, the current amount of free heap memory in
   * megabytes, and the floating point number of database cache misses per
   * imported entry since the previous progress report.
   */
  public static final int MSGID_JEB_IMPORT_CACHE_AND_MEMORY_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 97;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the name of the index file having no data
   * to be loaded.
   */
  public static final int MSGID_JEB_INDEX_MERGE_NO_DATA =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 98;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes two arguments, the number of source intermediate data
   * files to be merged, and the name of the destination index file.
   */
  public static final int MSGID_JEB_INDEX_MERGE_START =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 99;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, the name of the index file that has been
   * loaded.
   */
  public static final int MSGID_JEB_INDEX_MERGE_COMPLETE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 100;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes four arguments, the total number of records
   * checked, the number of errors found, the total time in seconds taken by the
   * verify process, and the floating point overall average number of records
   * checked per second.
   */
  public static final int MSGID_JEB_VERIFY_CLEAN_FINAL_STATUS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 101;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes one argument, the number of records that
   * reference more than one entry.
   */
  public static final int MSGID_JEB_VERIFY_MULTIPLE_REFERENCE_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 102;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes one argument, the number of records that
   * exceeded the entry limit.
   */
  public static final int MSGID_JEB_VERIFY_ENTRY_LIMIT_EXCEEDED_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 103;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes one argument, the floating point average
   * number of entries referenced per record (not including records that
   * exceed the entry limit).
   */
  public static final int MSGID_JEB_VERIFY_AVERAGE_REFERENCE_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 104;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes one argument, the maximum number of entries
   * referenced by a single record (not including records that exceed the
   * entry limit).
   */
  public static final int MSGID_JEB_VERIFY_MAX_REFERENCE_COUNT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 105;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes four arguments, the total number of entries
   * checked, the number of errors found, the total time in seconds taken by the
   * verify process, and the floating point overall average number of entries
   * checked per second.
   */
  public static final int MSGID_JEB_VERIFY_FINAL_STATUS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 106;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes no arguments.
   */
  public static final int MSGID_JEB_VERIFY_ENTRY_LIMIT_STATS_HEADER =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 107;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes five arguments, the name of the index file,
   * the number of records that exceed the entry limit, and the minimum, maximum
   * and median number of entries referenced by those records.
   */
  public static final int MSGID_JEB_VERIFY_ENTRY_LIMIT_STATS_ROW =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 108;



  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes three arguments, the number of records
   * checked so far, the number of errors found so far, and the floating point
   * average number of records checked per second since the previous progress
   * report.
   */
  public static final int MSGID_JEB_VERIFY_PROGRESS_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 109;



  /**
   * The message ID used to log an informational message in the verify index
   * process. This message takes two arguments, the current amount of free heap
   * memory in megabytes, and the floating point number of database cache misses
   * per record processed since the previous progress report.
   */
  public static final int MSGID_JEB_VERIFY_CACHE_AND_MEMORY_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 110;



  /**
   * The message ID used to return an informative error message to the client
   * when the cookie sent by the client in a paged results control
   * was not recognized.  This message takes one argument, which is a string
   * containing the hex bytes of the cookie.
   */
  public static final int MSGID_JEB_INVALID_PAGED_RESULTS_COOKIE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 111;



  /**
   * The message ID used to return an informative message to the client when
   * a referral result was sent because of a referral entry at or above the
   * target entry.  This message takes one string argument which is the DN
   * of the referral entry.
   */
  public static final int MSGID_JEB_REFERRAL_RESULT_MESSAGE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 112;



  /**
   * The message ID used to log an informational message in the import process.
   * This message takes one argument, a string representation of the JE
   * environment configuration properties.
   */
  public static final int MSGID_JEB_IMPORT_ENVIRONMENT_CONFIG =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 119;


  /**
   * The message ID used to indicate that an LDIF import pass has completed and
   * it is time to begin the intermediate index merge process.  This takes a
   * single argument, which is the pass number that has completed.
   */
  public static final int MSGID_JEB_IMPORT_BEGINNING_INTERMEDIATE_MERGE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 121;



  /**
   * The message ID used to indicate that an LDIF import processing has
   * completed and it is time to begin the final index merge process.  This does
   * not take any arguments.
   */
  public static final int MSGID_JEB_IMPORT_BEGINNING_FINAL_MERGE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 122;



  /**
   * The message ID used to indicate that the intermediate index merge has
   * completed and that LDIF processing will resume.  This takes a single
   * argument, which is the length of time in seconds that the merge was in
   * progress.
   */
  public static final int MSGID_JEB_IMPORT_RESUMING_LDIF_PROCESSING =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 123;



  /**
   * The message ID used to indicate that the final index merge has completed.
   * This takes a single argument, which is the length of time in seconds that
   * the merge was in progress.
   */
  public static final int MSGID_JEB_IMPORT_FINAL_MERGE_COMPLETED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 124;



  /**
   * The message ID of an error indicating the version of DatabaseEntry is
   * incompatible and can not be decoded.
   */
  public static final int MSGID_JEB_INCOMPATIBLE_ENTRY_VERSION =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 126;



  /**
   * The message ID for the string representation of the result code that will
   * be used for operations that failed because the operation lookthrough
   *  limit was exceeded.
   */
  public static final int MSGID_JEB_LOOKTHROUGH_LIMIT_EXCEEDED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 127;



  /**
   * The message ID of an error indicating that the file permissions for the
   * database directory was not set.
   */
  public static final int MSGID_JEB_SET_PERMISSIONS_FAILED =
      CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_WARNING | 128;

  /**
   * The message ID of an error indicating the entry count of a container can
   * not be determined.
   */
  public static final int MSGID_JEB_GET_ENTRY_COUNT_FAILED =
      CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_WARNING | 129;



  /**
   * The message ID used to indicate that a configuration attribute change
   * will not take effect until the backend is restarted.  This message
   * takes one argument, the name of the configuration attribute.
   */
  public static final int MSGID_JEB_CONFIG_ATTR_REQUIRES_RESTART =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 130;

  /**
   * The message ID used to log an informational message in the rebuild index
   * process.  This message takes four arguments: the completed percentage,
   * the number of entries processed, the total number of entries to process,
   * the floating point average number of records processed per second
   * since the previous progress.
   */
  public static final int MSGID_JEB_REBUILD_PROGRESS_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 131;



  /**
   * The message ID used to log an informational message in the rebuild index
   * process. This message takes two arguments: the current amount of free heap
   * memory in megabytes, and the floating point number of database cache misses
   * per record processed since the previous progress report.
   */
  public static final int MSGID_JEB_REBUILD_CACHE_AND_MEMORY_REPORT =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 132;


  /**
   * The message ID used to log an informational message in the verify index
   * process.  This message takes seven arguments: the total number of entries
   * processed, the total number of rebuilt entries, the total number
   * of duplicated entries, the total number of skipped entries, the total time
   * for the rebuild process to complete, and the floating point overall number
   * of records processed per second.
   */
  public static final int MSGID_JEB_REBUILD_FINAL_STATUS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 133;

  /**
   * The message ID used to indicate that an error occured in an index rebuild
   * thread and it is terminated. This message takes two arguments: the name
   * of the index rebuild thread that failed and the exception that caused
   * the failure.
   */
  public static final int MSGID_JEB_REBUILD_INDEX_FAILED =
      CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 134;

  /**
   * The message ID used to indicate that an error occured while inserting
   * an entry into a database/index during the rebuild process. This message
   * takes two arguments: the name of the database/index being inserted into,
   * and the exception that caused the failure.
   */
  public static final int MSGID_JEB_REBUILD_INSERT_ENTRY_FAILED =
      CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 135;

  /**
   * The message ID used to indicate that another rebuild process for an index
   * is already in progress. This message takes the name of the conflicting
   * index as the argument.
   */
  public static final int MSGID_JEB_REBUILD_INDEX_CONFLICT =
      CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 136;

  /**
   * The message ID used to log an information message about the rebuild job.
   * This message takes 2 arguments: the names of the index in the rebuild job
   * and the number of total records to process.
   */
  public static final int MSGID_JEB_REBUILD_START =
      CATEGORY_MASK_JEB | SEVERITY_MASK_INFORMATIONAL | 137;

  /**
   * The message ID of an error indicating that rebuilding of system indexes
   * can not be done while the backend is online.
   */
  public static final int MSGID_JEB_REBUILD_BACKEND_ONLINE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 138;

  /**
   * The message ID to use if an error occurs while attempting to retrieve an
   * entry to examine while sorting.  This takes two arguments, which are a
   * string representation of the entry ID and a message explaining the
   * problem that occurred.
   */
  public static final int MSGID_ENTRYIDSORTER_CANNOT_EXAMINE_ENTRY =
       CATEGORY_MASK_JEB | SEVERITY_MASK_SEVERE_ERROR | 139;

  /**
   * The message ID of an error indicating that the base entry of a search
   * operation does not exist.  This message takes one string argument which is
   * the DN of the search base entry.
   */
  public static final int MSGID_JEB_SEARCH_CANNOT_SORT_UNINDEXED =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 140;

  /**
   * The message ID to use if a VLV request has a negative start position.  This
   * does not take any arguments.
   */
  public static final int MSGID_ENTRYIDSORTER_NEGATIVE_START_POS =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 141;

  /**
   * The message ID to use if a VLV request has an offset beyond the end of the
   * entry set.  This takes two arguments, which are the provided offset and the
   * list size.
   */
  public static final int MSGID_ENTRYIDSORTER_OFFSET_TOO_LARGE =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 142;

  /**
   * The message ID to use if a VLV request specifies a target value that is
   * larger than all values in the sort list.  This does not take any arguments.
   */
  public static final int MSGID_ENTRYIDSORTER_TARGET_VALUE_NOT_FOUND =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 143;


  /**
   * The message ID of an error indicating that the search request included both
   * the paged results control and the VLV control.  This does not take any
   * arguments.
   */
  public static final int MSGID_JEB_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV =
       CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 144;

  /**
   * The message ID of an error indicating that unindexed searches are not
   * allowed without the unindexed search prilvilege. This does not take
   * any arguments.
   */
  public static final int MSGID_JEB_SEARCH_UNINDEXED_INSUFFICIENT_PRIVILEGES =
      CATEGORY_MASK_JEB | SEVERITY_MASK_MILD_ERROR | 145;

  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_JEB_INCORRECT_ROUTING,
                    "The backend does not contain that part of the Directory " +
                    "Information Tree pertaining to the entry " +
                    "'%s'");
    registerMessage(MSGID_JEB_OPEN_DATABASE_FAIL,
                    "The database could not be opened: %s");
    registerMessage(MSGID_JEB_OPEN_ENV_FAIL,
                    "The database environment could not be opened: %s");
    registerMessage(MSGID_JEB_HIGHEST_ID_FAIL,
                    "The database highest entry identifier could not be " +
                    "determined");
    registerMessage(MSGID_JEB_FUNCTION_NOT_SUPPORTED,
                    "The requested operation is not supported by this " +
                    "backend");
    registerMessage(MSGID_JEB_CREATE_FAIL,
                    "The backend database directory could not be created: %s");
    registerMessage(MSGID_JEB_REMOVE_FAIL,
                    "The backend database files could not be removed: %s");
    registerMessage(MSGID_JEB_DIRECTORY_INVALID,
                    "The backend database directory '%s' is not a valid " +
                    "directory");
    registerMessage(MSGID_JEB_MISSING_DN2ID_RECORD,
                    "The DN database does not contain a record for '%s'");
    registerMessage(MSGID_JEB_MISSING_ID2ENTRY_RECORD,
                    "The entry database does not contain a record for ID %s");
    registerMessage(MSGID_JEB_ENTRY_DATABASE_CORRUPT,
                    "The entry database does not contain a valid record " +
                    "for ID %s");
    registerMessage(MSGID_JEB_DATABASE_EXCEPTION,
                    "Database exception: %s");
    registerMessage(MSGID_CONFIG_DESCRIPTION_INDEX_ATTRIBUTE,
                    "The attribute type name of the attribute index");
    registerMessage(MSGID_CONFIG_DESCRIPTION_INDEX_TYPE,
                    "The kind of indexing to be enabled on an attribute " +
                    "index. Permitted values include \"equality\", " +
                    "\"presence\", \"substring\" and \"ordering\")");
    registerMessage(MSGID_CONFIG_DESCRIPTION_INDEX_ENTRY_LIMIT,
                    "A performance tuning parameter for attribute indexes. " +
                    "The entry limit of an attribute index, where " +
                    "a value of 0 means there is no threshold. " +
                    "When the number of entries " +
                    "matching an index value reaches the limit, the " +
                    "value is no longer maintained in the index");
    registerMessage(MSGID_CONFIG_DESCRIPTION_INDEX_SUBSTRING_LENGTH,
                    "The length of substrings in a substring index");
    registerMessage(MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE,
                    "The attribute '%s' cannot have indexing of type '%s' " +
                    "because it does not have a corresponding matching rule");
    registerMessage(MSGID_JEB_UNCHECKED_EXCEPTION,
                    "Unchecked exception during database transaction");
    registerMessage(MSGID_JEB_CLEAN_DATABASE_START,
                    "Starting database cleaning on %d log file(s) in '%s'");
    registerMessage(MSGID_JEB_CLEAN_DATABASE_MARKED,
                    "Marked %d log file(s) for cleaning");
    registerMessage(MSGID_JEB_CLEAN_DATABASE_FINISH,
                    "Finished database cleaning; " +
                    "now %d log file(s) remaining");
    registerMessage(MSGID_JEB_SUBTREE_DELETE_SIZE_LIMIT_EXCEEDED,
                    "Exceeded the administrative limit on the number of " +
                    "entries that may be deleted in a subtree delete " +
                    "operation. The number of entries actually deleted was " +
                    "%d. The operation may be retried until all entries " +
                    "in the subtree have been deleted");
    registerMessage(MSGID_JEB_DELETED_ENTRY_COUNT,
                    "The number of entries deleted was %d");
    registerMessage(MSGID_JEB_DUPLICATE_CONFIG_ENTRY,
                    "The configuration entry '%s' will be ignored. " +
                    "Only one configuration entry with object class '%s' is " +
                    "allowed");
    registerMessage(MSGID_JEB_CONFIG_ENTRY_NOT_RECOGNIZED,
                    "The configuration entry '%s' will be ignored " +
                    "because it is not recognized");
    registerMessage(MSGID_JEB_INDEX_ATTRIBUTE_TYPE_NOT_FOUND,
                    "The index configuration entry '%s' will be ignored " +
                    "because it specifies an unknown attribute type '%s'");
    registerMessage(MSGID_JEB_DUPLICATE_INDEX_CONFIG,
                    "The index configuration entry '%s' will be ignored " +
                    "because it specifies the attribute type '%s', " +
                    "which has already been defined in another " +
                    "index configuration entry");
    registerMessage(MSGID_JEB_IO_ERROR,
                    "I/O error during backend operation: %s");
    registerMessage(MSGID_JEB_BACKEND_STARTED,
                    "A database backend containing %d entries has started");
    registerMessage(MSGID_JEB_IMPORT_PARENT_NOT_FOUND,
                    "The parent entry '%s' does not exist");
    registerMessage(MSGID_JEB_IMPORT_ENTRY_EXISTS,
                    "The entry exists and the import options do not " +
                    "allow it to be replaced");
    registerMessage(MSGID_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED,
                    "There is no index configured for attribute type '%s'");
    registerMessage(MSGID_JEB_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV,
                    "The requested search operation included both the simple " +
                    "paged results control and the virtual list view " +
                    "control.  These controls are mutually exclusive and " +
                    "cannot be used together");
    registerMessage(MSGID_JEB_SEARCH_NO_SUCH_OBJECT,
                    "The search base entry '%s' does not exist");
    registerMessage(MSGID_JEB_SEARCH_CANNOT_SORT_UNINDEXED,
                    "The search results cannot be sorted because the given " +
                    "search request is not indexed");
    registerMessage(MSGID_JEB_ADD_NO_SUCH_OBJECT,
                    "The entry '%s' cannot be added because its parent " +
                    "entry does not exist");
    registerMessage(MSGID_JEB_DELETE_NO_SUCH_OBJECT,
                    "The entry '%s' cannot be removed because it does " +
                    "not exist");
    registerMessage(MSGID_JEB_MODIFY_NO_SUCH_OBJECT,
                    "The entry '%s' cannot be modified because it does " +
                    "not exist");
    registerMessage(MSGID_JEB_MODIFYDN_NO_SUCH_OBJECT,
                    "The entry '%s' cannot be renamed because it does " +
                    "not exist");
    registerMessage(MSGID_JEB_ADD_ENTRY_ALREADY_EXISTS,
                    "The entry '%s' cannot be added because an entry with " +
                    "that name already exists");
    registerMessage(MSGID_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF,
                    "The entry '%s' cannot be removed because it has " +
                    "subordinate entries");
    registerMessage(MSGID_JEB_MODIFYDN_ALREADY_EXISTS,
                    "The entry cannot be renamed to '%s' because an entry " +
                    "with that name already exists");
    registerMessage(MSGID_JEB_NEW_SUPERIOR_NO_SUCH_OBJECT,
                    "The entry cannot be moved because the new parent " +
                    "entry '%s' does not exist");
    registerMessage(MSGID_JEB_CACHE_SIZE_AFTER_PRELOAD,
                    "The database cache is %d MB after pre-loading");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_GET_MAC,
                    "An error occurred while attempting to obtain the %s MAC " +
                    "provider to create the signed hash for the backup:  %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_GET_DIGEST,
                    "An error occurred while attempting to obtain the %s " +
                    "message digest to create the hash for the backup:  %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_CREATE_ARCHIVE_FILE,
                    "An error occurred while trying to create the database " +
                    "archive file %s in directory %s:  %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_GET_CIPHER,
                    "An error occurred while attempting to obtain the %s " +
                    "cipher to use to encrypt the backup:  %s");
    registerMessage(MSGID_JEB_BACKUP_ZIP_COMMENT,
                    "%s backup %s of backend %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_LIST_LOG_FILES,
                    "An error occurred while attempting to obtain a list " +
                    "of the files in directory %s to include in the database " +
                    "backup:  %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_WRITE_ARCHIVE_FILE,
                    "An error occurred while attempting to back up database " +
                    "file %s:  %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_CLOSE_ZIP_STREAM,
                    "An error occurred while trying to close the database " +
                    "archive file %s in directory %s:  %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_UPDATE_BACKUP_DESCRIPTOR,
                    "An error occurred while attempting to update the backup " +
                    "descriptor file %s with information about the database " +
                    "backup:  %s");
    registerMessage(MSGID_JEB_BACKUP_UNSIGNED_HASH_ERROR,
                    "The computed hash of backup %s is different to the " +
                    "value computed at time of backup");
    registerMessage(MSGID_JEB_BACKUP_SIGNED_HASH_ERROR,
                    "The computed signed hash of backup %s is different to " +
                    "the value computed at time of backup");
    registerMessage(MSGID_JEB_INCR_BACKUP_REQUIRES_FULL,
                    "A full backup must be taken before an incremental " +
                    "backup can be taken");
    registerMessage(MSGID_JEB_CANNOT_RENAME_RESTORE_DIRECTORY,
                    "The directory %s, containing the files restored from " +
                    "backup, could not be renamed to the backend directory " +
                    "%s");
    registerMessage(MSGID_JEB_INCR_BACKUP_FROM_WRONG_BASE,
                    "One of the following base backup IDs must be specified " +
                    "for the incremental backup: %s");
    registerMessage(MSGID_JEB_CANNOT_CREATE_BACKUP_TAG_FILE,
                    "The backup tag file %s could not be created in %s");
    registerMessage(MSGID_JEB_BACKUP_CANNOT_RESTORE,
                    "An error occurred while attempting to restore the files " +
                    "from backup %s: %s");
    registerMessage(MSGID_JEB_BACKUP_MISSING_BACKUPID,
                    "The information for backup %s could not be found in " +
                    "the backup directory %s");
    registerMessage(MSGID_JEB_BACKUP_FILE_UNCHANGED,
                    "Not changed: %s");
    registerMessage(MSGID_JEB_BACKUP_CLEANER_ACTIVITY,
                    "Including %s additional log file(s) due to cleaner " +
                    "activity");
    registerMessage(MSGID_JEB_BACKUP_VERIFY_FILE,
                    "Verifying: %s");
    registerMessage(MSGID_JEB_BACKUP_RESTORED_FILE,
                    "Restored: %s (size %d)");
    registerMessage(MSGID_JEB_BACKUP_ARCHIVED_FILE,
                    "Archived: %s");
    registerMessage(MSGID_JEB_EXPORT_FINAL_STATUS,
                    "Exported %d entries and skipped %d in %d seconds " +
                    "(average rate %.1f/sec)");
    registerMessage(MSGID_JEB_EXPORT_PROGRESS_REPORT,
                    "Exported %d records and skipped %d " +
                    "(recent rate %.1f/sec)");
    registerMessage(MSGID_JEB_IMPORT_THREAD_COUNT,
                    "Import thread count = %d");
    registerMessage(MSGID_JEB_IMPORT_BUFFER_SIZE,
                    "Buffer size per thread = %,d");
    registerMessage(MSGID_JEB_IMPORT_LDIF_PROCESSING_TIME,
                    "LDIF processing took %d seconds");
    registerMessage(MSGID_JEB_IMPORT_INDEX_PROCESSING_TIME,
                    "Index processing took %d seconds");
    registerMessage(MSGID_JEB_IMPORT_BEGINNING_INTERMEDIATE_MERGE,
                    "Ending LDIF import pass %d because the pass size has " +
                    "been reached.  Beginning the intermediate index merge");
    registerMessage(MSGID_JEB_IMPORT_BEGINNING_FINAL_MERGE,
                    "End of LDIF reached.  Beginning final index merge");
    registerMessage(MSGID_JEB_IMPORT_RESUMING_LDIF_PROCESSING,
                    "Intermediate index merge processing complete (index " +
                    "processing time %d seconds).  Resuming LDIF processing");
    registerMessage(MSGID_JEB_IMPORT_FINAL_MERGE_COMPLETED,
                    "Final index merge complete (processing time %d seconds)");
    registerMessage(MSGID_JEB_IMPORT_CLOSING_DATABASE,
                    "Flushing data to disk");
    registerMessage(MSGID_JEB_IMPORT_FINAL_STATUS,
                    "Processed %d entries, imported %d, skipped %d, and " +
                    "rejected %d in %d seconds (average rate %.1f/sec)");
    registerMessage(MSGID_JEB_IMPORT_ENTRY_LIMIT_EXCEEDED_COUNT,
                    "Number of index values that exceeded the entry limit: %d");
    registerMessage(MSGID_JEB_IMPORT_PROGRESS_REPORT,
                    "Processed %d entries, skipped %d, and rejected %d " +
                    "(recent rate %.1f/sec)");
    registerMessage(MSGID_JEB_IMPORT_CACHE_AND_MEMORY_REPORT,
                    "Free memory = %d MB, Cache miss rate = %.1f/entry");
    registerMessage(MSGID_JEB_INDEX_MERGE_NO_DATA,
                    "There is no data to be loaded into the %s index");
    registerMessage(MSGID_JEB_INDEX_MERGE_START,
                    "Starting %d-way merge to load the %s index");
    registerMessage(MSGID_JEB_INDEX_MERGE_COMPLETE,
                    "The %s index has been loaded");
    registerMessage(MSGID_JEB_VERIFY_CLEAN_FINAL_STATUS,
                    "Checked %d records and found %d error(s) in %d seconds " +
                    "(average rate %.1f/sec)");
    registerMessage(MSGID_JEB_VERIFY_MULTIPLE_REFERENCE_COUNT,
                    "Number of records referencing more than one entry: %d");
    registerMessage(MSGID_JEB_VERIFY_ENTRY_LIMIT_EXCEEDED_COUNT,
                    "Number of records that exceed the entry limit: %d");
    registerMessage(MSGID_JEB_VERIFY_AVERAGE_REFERENCE_COUNT,
                    "Average number of entries referenced is %.2f/record");
    registerMessage(MSGID_JEB_VERIFY_MAX_REFERENCE_COUNT,
                    "Maximum number of entries referenced " +
                    "by any record is %d");
    registerMessage(MSGID_JEB_VERIFY_FINAL_STATUS,
                    "Checked %d entries and found %d error(s) in %d seconds " +
                    "(average rate %.1f/sec)");
    registerMessage(MSGID_JEB_VERIFY_ENTRY_LIMIT_STATS_HEADER,
                    "Statistics for records that have exceeded the " +
                    "entry limit:");
    registerMessage(MSGID_JEB_VERIFY_ENTRY_LIMIT_STATS_ROW,
                    "  File %s has %d such record(s) min=%d max=%d median=%d");
    registerMessage(MSGID_JEB_VERIFY_PROGRESS_REPORT,
                    "Processed %d records and found %d error(s) " +
                    "(recent rate %.1f/sec)");
    registerMessage(MSGID_JEB_VERIFY_CACHE_AND_MEMORY_REPORT,
                    "Free memory = %d MB, Cache miss rate = %.1f/record");
    registerMessage(MSGID_JEB_CONFIG_ATTR_REQUIRES_RESTART,
                    "The change to the %s attribute will not take effect " +
                    "until the backend is restarted");
    registerMessage(MSGID_JEB_INVALID_PAGED_RESULTS_COOKIE,
                    "The following paged results control cookie value was " +
                    "not recognized: %s");
    registerMessage(MSGID_JEB_REFERRAL_RESULT_MESSAGE,
                    "A referral entry %s indicates that the operation must " +
                    "be processed at a different server");
    registerMessage(MSGID_JEB_IMPORT_ENVIRONMENT_CONFIG,
                    "Database environment properties: %s");
    registerMessage(MSGID_JEB_INCOMPATIBLE_ENTRY_VERSION,
                    "Entry record with ID %s is not compatible with this " +
                    "version of the backend database. " +
                    "Entry version: %x");
    registerMessage(MSGID_JEB_LOOKTHROUGH_LIMIT_EXCEEDED,
                    "This search operation has checked the maximum of %d " +
                    "entries for matches");
    registerMessage(MSGID_JEB_SET_PERMISSIONS_FAILED,
                    "Unable to set file permissions for the backend database " +
                    "directory %s");
    registerMessage(MSGID_JEB_GET_ENTRY_COUNT_FAILED,
                    "Unable to determine the total number of entries in the " +
                    "container: %s");
    registerMessage(MSGID_JEB_REBUILD_START,
                    "Rebuild of index(es) %s started with %d total records " +
                    "to process");
    registerMessage(MSGID_JEB_REBUILD_PROGRESS_REPORT,
                    "%.1f%% Completed. Processed %d/%d records. " +
                    "(recent rate %.1f/sec)");
    registerMessage(MSGID_JEB_REBUILD_CACHE_AND_MEMORY_REPORT,
                    "Free memory = %d MB, Cache miss rate = %.1f/record");
    registerMessage(MSGID_JEB_REBUILD_FINAL_STATUS,
                    "Rebuild complete. Processed %d records in %d seconds " +
                    "(average rate %.1f/sec)");
    registerMessage(MSGID_JEB_REBUILD_INDEX_FAILED,
                    "An error occured while rebuilding index %s: %s");
    registerMessage(MSGID_JEB_REBUILD_INSERT_ENTRY_FAILED,
                    "An error occured while inserting entry into the %s " +
                    "database/index: %s");
    registerMessage(MSGID_JEB_REBUILD_INDEX_CONFLICT,
                    "Another rebuild of index %s is already in progress");
    registerMessage(MSGID_JEB_REBUILD_BACKEND_ONLINE,
                    "Rebuilding system index(es) must be done with the " +
                    "backend containing the base DN disabled");


    registerMessage(MSGID_ENTRYIDSORTER_CANNOT_EXAMINE_ENTRY,
                    "Unable to examine the entry with ID %s for sorting " +
                    "purposes:  %s");
    registerMessage(MSGID_ENTRYIDSORTER_NEGATIVE_START_POS,
                    "Unable to process the virtual list view request because " +
                    "the target start position was before the beginning of " +
                    "the result set");
    registerMessage(MSGID_ENTRYIDSORTER_OFFSET_TOO_LARGE,
                    "Unable to process the virtual list view request because " +
                    "the target offset %d was greater than the total number " +
                    "of results in the list (%d)");
    registerMessage(MSGID_ENTRYIDSORTER_TARGET_VALUE_NOT_FOUND,
                    "Unable to prcess the virtual list view request because " +
                    "no entry was found in the result set with a sort value " +
                    "greater than or equal to the provided assertion value");
    registerMessage(MSGID_JEB_SEARCH_UNINDEXED_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to perform an " +
                    "unindexed search");
  }
}
