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
   * The message ID for the message that will be used if an error occured
   * while writing a log record.  This takes a two arguments, which
   * are the logger that encountered the error and  a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LOGGER_ERROR_WRITING_RECORD =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID for the message that will be used if an error occured
   * while opening a log file.  This takes a two arguments, which
   * are the logger that encountered the error and  a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LOGGER_ERROR_OPENING_FILE =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 2;



  /**
   * The message ID for the message that will be used if an error occured
   * while closing a log file.  This takes a two arguments, which
   * are the logger that encountered the error and  a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LOGGER_ERROR_CLOSING_FILE =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 3;



  /**
   * The message ID for the message that will be used if an error occured
   * while flushing the writer buffer.  This takes a two arguments, which
   * are the logger that encountered the error and  a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_LOGGER_ERROR_FLUSHING_BUFFER =
       CATEGORY_MASK_LOG | SEVERITY_MASK_SEVERE_ERROR | 4;



  /**
   * The message ID for the message that will be used if the specified
   * string is not a valid error severity name. This takes the name
   * of the invalid severity as the argument.
   */
  public static final int MSGID_ERROR_LOGGER_INVALID_SEVERITY =
        CATEGORY_MASK_LOG | SEVERITY_MASK_MILD_WARNING | 5;


    /**
   * The message ID for the message that will be used if the specified
   * string is not a valid error category name. This takes the name
   * of the invalid category as the argument.
   */
  public static final int MSGID_ERROR_LOGGER_INVALID_CATEGORY =
        CATEGORY_MASK_LOG | SEVERITY_MASK_MILD_WARNING | 6;



  /**
   * The message ID for the message that will be used if the specified
   * string is not a valid error override severity. This takes the name
   * of the invalid severity as the argument.
   */
  public static final int MSGID_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY =
        CATEGORY_MASK_LOG | SEVERITY_MASK_MILD_WARNING | 7;



  /**
   * The message ID for the message that will be used if an error occured
   * while setting file permissions on a log file. This takes the name of the
   * file as the argument.
   */
  public static final int MSGID_LOGGER_SET_PERMISSION_FAILED =
        CATEGORY_MASK_LOG | SEVERITY_MASK_MILD_WARNING | 8;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_LOGGER_ERROR_WRITING_RECORD,
                    "Unable to write log record for logger  " +
                    "%s: %s. Any further writing errors will be ignored");
    registerMessage(MSGID_LOGGER_ERROR_OPENING_FILE,
                    "Unable to open log file %s for logger %s: %s");
    registerMessage(MSGID_LOGGER_ERROR_CLOSING_FILE,
                    "Unable to close log file for logger %s: %s");
    registerMessage(MSGID_LOGGER_ERROR_FLUSHING_BUFFER,
                    "Unable to flush writer buffer for logger %s: %s");
    registerMessage(MSGID_ERROR_LOGGER_INVALID_SEVERITY,
                    "Invalid error log severity %s");
    registerMessage(MSGID_ERROR_LOGGER_INVALID_CATEGORY,
                    "Invalid error log category %s");
    registerMessage(MSGID_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY,
                    "Invalid override of severity level %s");
    registerMessage(MSGID_LOGGER_SET_PERMISSION_FAILED,
                    "Unable to set file permissions for the log file %s");

  }
}

