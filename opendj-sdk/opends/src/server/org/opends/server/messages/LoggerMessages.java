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
 * messages associated with the server loggers.
 */
public class LoggerMessages
{
  /**
   * The message ID for the message that will be used if the access logger
   * cannot add an appropriate log handler.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LOG_ACCESS_CANNOT_ADD_FILE_HANDLER =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID for the message that will be used if the error logger
   * cannot add an appropriate log handler.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LOG_ERROR_CANNOT_ADD_FILE_HANDLER =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 2;



  /**
   * The message ID for the message that will be used if the debug logger
   * cannot add an appropriate log handler.  This takes a single argument, which
   * is a string representation of the exception that was caught.
   */
  public static final int MSGID_LOG_DEBUG_CANNOT_ADD_FILE_HANDLER =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 3;


  /**
   * The message ID for the message that will be used as the description of the
   * configuration attribute specifying the nickname of the certificate to use
   * for SSL and StartTLS communication.
   */
  public static final int MSGID_LOG_DESCRIPTION_SSL_CERT_NICKNAME =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 4;

  /**
   * The message ID for the description of the configuration attribute that
   * specifies the file size limit for rotation.
   */
  public static final int MSGID_LOGGER_ROTATION_SIZE_LIMIT =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 5;

  /**
   * The message ID for the description of the configuration attribute that
   * specifies the time limit for rotation.
   */
  public static final int MSGID_LOGGER_ROTATION_FIXED_TIME_LIMIT =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 6;


  /**
   * The message ID for the description of the configuration attribute that
   * specifies the number of files for the retention policy.
   */
  public static final int MSGID_LOGGER_RETENTION_NUMBER_OF_FILES =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 7;

  /**
   * The message ID for the description of the configuration attribute that
   * specifies the disk space used for the size based retention policy.
   */
  public static final int MSGID_LOGGER_RETENTION_DISK_SPACE_USED =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 8;


  /**
   * The message ID for the description of the configuration attribute that
   * specifies the thread time interval.
   */
  public static final int MSGID_LOGGER_THREAD_INTERVAL =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 9;

  /**
   * The message ID for the description of the configuration attribute that
   * specifies the log buffer size.
   */
  public static final int MSGID_LOGGER_BUFFER_SIZE =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 10;


  /**
   * The message ID for the description of the configuration attribute that
   * specifies the free disk space allowed.
   */
  public static final int MSGID_LOGGER_RETENTION_FREE_DISK_SPACE =
       CATEGORY_MASK_LOG | SEVERITY_MASK_INFORMATIONAL | 11;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_LOG_ACCESS_CANNOT_ADD_FILE_HANDLER,
                    "Unable to add a file handler for the Directory Server " +
                    "access logger:  %s.");
    registerMessage(MSGID_LOG_ERROR_CANNOT_ADD_FILE_HANDLER,
                    "Unable to add a file handler for the Directory Server " +
                    "error logger:  %s.");
    registerMessage(MSGID_LOG_DEBUG_CANNOT_ADD_FILE_HANDLER,
                    "Unable to add a file handler for the Directory Server " +
                    "debug logger:  %s.");
    registerMessage(MSGID_LOG_DESCRIPTION_SSL_CERT_NICKNAME,
                    "Specifies the nickname of the certificate that the " +
                    "connection handler should use when accepting SSL-based " +
                    "connections or performing StartTLS negotiation.  " +
                    "Changes to this configuration attribute will not take " +
                    "effect until the connection handler is disabled and " +
                    "re-enabled, or until the Directory Server is restarted.");
    registerMessage(MSGID_LOGGER_ROTATION_SIZE_LIMIT,
                    "Specifies the size limit for the file before rotation " +
                    "takes place.");
    registerMessage(MSGID_LOGGER_ROTATION_FIXED_TIME_LIMIT,
                    "Specifies the time interval before the log file rotation" +
                    " takes place.");
    registerMessage(MSGID_LOGGER_RETENTION_NUMBER_OF_FILES,
                    "Specifies the number of log files that need to " +
                    " be retained.");
    registerMessage(MSGID_LOGGER_RETENTION_DISK_SPACE_USED,
                    "Specifies the amount of disk space that log files " +
                    " can use.");
    registerMessage(MSGID_LOGGER_THREAD_INTERVAL,
                    "Specifies the time interval that the logger thread " +
                    " wakes up after.");
    registerMessage(MSGID_LOGGER_BUFFER_SIZE,
                    "Specifies the log file buffer size.");
    registerMessage(MSGID_LOGGER_RETENTION_FREE_DISK_SPACE,
                    "Specifies the free disk space allowed.");

  }
}

