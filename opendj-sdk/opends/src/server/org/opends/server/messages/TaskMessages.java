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



import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the tools.
 */
public class TaskMessages
{
  /**
   * The message ID of the message that will be used if a backend could not be
   * enabled.
   */
  public static final int MSGID_TASK_CANNOT_ENABLE_BACKEND =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID of the message that will be used if a backend could not be
   * disabled.
   */
  public static final int MSGID_TASK_CANNOT_DISABLE_BACKEND =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 2;



  /**
   * The message ID for the shutdown message that will be used if the Directory
   * Server shutdown process is initiated by a task that does not include its
   * own shutdown message.
   */
  public static final int MSGID_TASK_SHUTDOWN_DEFAULT_MESSAGE =
       CATEGORY_MASK_TASK | SEVERITY_MASK_INFORMATIONAL | 3;



  /**
   * The message ID for the shutdown message that will be used if the Directory
   * Server shutdown process is initiated by a task that contains a custom
   * shutdown message.
   */
  public static final int MSGID_TASK_SHUTDOWN_CUSTOM_MESSAGE =
       CATEGORY_MASK_TASK | SEVERITY_MASK_INFORMATIONAL | 4;



  /**
   * The message ID for the shutdown message that will be used if no schema file
   * names were provided.  This takes two arguments, which are the name of the
   * attribute and the DN of the entry in which the file names should have been
   * given.
   */
  public static final int MSGID_TASK_ADDSCHEMAFILE_NO_FILENAME =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 5;



  /**
   * The message ID for the shutdown message that will be used if a specified
   * schema file does not exist in the schema directory.  This takes two
   * arguments, which are the name of the schema file and the path to the schema
   * directory.
   */
  public static final int MSGID_TASK_ADDSCHEMAFILE_NO_SUCH_FILE =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 6;



  /**
   * The message ID for the shutdown message that will be used if an error
   * occurs while attempting to check for the existence of a schema file in the
   * schema directory.  This takes three arguments, which are the name of the
   * file, the path to the schema directory, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_TASK_ADDSCHEMAFILE_ERROR_CHECKING_FOR_FILE =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 7;



  /**
   * The message ID for the shutdown message that will be used if an error
   * occurs while trying to read and load the contents of a schema file into the
   * server schema.  This takes two arguments, which are the name of the schema
   * file and a message explaining the problem that occurred.
   */
  public static final int MSGID_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 8;



  /**
   * The message ID for the message that will be used if the server is unable to
   * obtain a write lock on the server schema.  This takes a single argument,
   * which is the DN of the schema entry.
   */
  public static final int MSGID_TASK_ADDSCHEMAFILE_CANNOT_LOCK_SCHEMA =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 9;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the add schema file task by a user that does not have the required
   * privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_ADDSCHEMAFILE_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 10;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the backend backup task by a user that does not have the required
   * privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_BACKUP_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 11;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the backend restore task by a user that does not have the required
   * privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_RESTORE_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 12;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the LDIF import task by a user that does not have the required
   * privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_LDIFIMPORT_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 13;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the LDIF export task by a user that does not have the required
   * privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_LDIFEXPORT_INSUFFICIENT_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 14;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the server shutdown task to restart the server by a user that does
   * not have the required privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_SHUTDOWN_INSUFFICIENT_RESTART_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 15;



  /**
   * The message ID for the message that will be used an attempt is made to
   * invoke the server shutdown task to shut down the server by a user that does
   * not have the required privileges.  This does not take any arguments.
   */
  public static final int MSGID_TASK_SHUTDOWN_INSUFFICIENT_SHUTDOWN_PRIVILEGES =
       CATEGORY_MASK_TASK | SEVERITY_MASK_SEVERE_ERROR | 16;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_TASK_CANNOT_ENABLE_BACKEND,
                    "The task could not enable a backend: %s");
    registerMessage(MSGID_TASK_CANNOT_DISABLE_BACKEND,
                    "The task could not disable a backend: %s");


    registerMessage(MSGID_TASK_SHUTDOWN_DEFAULT_MESSAGE,
                    "The Directory Server shutdown process has been " +
                    "initiated by task %s.");
    registerMessage(MSGID_TASK_SHUTDOWN_CUSTOM_MESSAGE,
                    "The Directory Server shutdown process has been " +
                    "initiated by task %s:  %s");
    registerMessage(MSGID_TASK_SHUTDOWN_INSUFFICIENT_RESTART_PRIVILEGES,
                    "You do not have sufficient privileges to initiate a " +
                    "Directory Server restart.");
    registerMessage(MSGID_TASK_SHUTDOWN_INSUFFICIENT_SHUTDOWN_PRIVILEGES,
                    "You do not have sufficient privileges to initiate a " +
                    "Directory Server shutdown.");


    registerMessage(MSGID_TASK_ADDSCHEMAFILE_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to modify the " +
                    "server schema.");
    registerMessage(MSGID_TASK_ADDSCHEMAFILE_NO_FILENAME,
                    "Unable to add one or more files to the server schema " +
                    "because no schema file names were provided in " +
                    "attribute %s of task entry %s.");
    registerMessage(MSGID_TASK_ADDSCHEMAFILE_NO_SUCH_FILE,
                    "Unable to add one or more files to the server schema " +
                    "because the specified schema file %s does not exist in " +
                    "schema directory %s.");
    registerMessage(MSGID_TASK_ADDSCHEMAFILE_ERROR_CHECKING_FOR_FILE,
                    "Unable to add one or more files to the server schema " +
                    "because an error occurred while attempting to determine " +
                    "whether file %s exists in schema directory %s:  %s.");
    registerMessage(MSGID_TASK_ADDSCHEMAFILE_ERROR_LOADING_SCHEMA_FILE,
                    "An error occurred while attempting to load the contents " +
                    "of schema file %s into the server schema:  %s.");
    registerMessage(MSGID_TASK_ADDSCHEMAFILE_CANNOT_LOCK_SCHEMA,
                    "Unable to add one or more files to the server schema " +
                    "because the server was unable to obtain a write lock on " +
                    "the schema entry %s after multiple attempts.");


    registerMessage(MSGID_TASK_BACKUP_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to initiate a " +
                    "Directory Server backup.");
    registerMessage(MSGID_TASK_RESTORE_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to initiate a " +
                    "Directory Server restore.");
    registerMessage(MSGID_TASK_LDIFIMPORT_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to initiate an " +
                    "LDIF import.");
    registerMessage(MSGID_TASK_LDIFEXPORT_INSUFFICIENT_PRIVILEGES,
                    "You do not have sufficient privileges to initiate an " +
                    "LDIF export.");
  }
}

