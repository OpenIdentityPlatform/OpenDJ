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
package org.opends.server.types;



import java.util.HashMap;

import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This enumeration defines the set of severity levels that may be
 * used when writing a message to an error logger.
 */
public enum ErrorLogSeverity
{
  /**
   * The error log severity that will be used for messages that
   * provide information about fatal errors which may force the server
   * to shut down or operate in a significantly degraded state.
   */
  FATAL_ERROR(MSGID_ERROR_SEVERITY_FATAL_ERROR),



  /**
   * The error log severity that will be used for debug messages
   * generated during general processing that may be useful enough to
   * warrant making them available without restarting the server in
   * debug mode.
   */
  GENERIC_DEBUG(MSGID_ERROR_SEVERITY_GENERIC_DEBUG),



  /**
   * The error log severity that will be used for messages that
   * provide information about significant events within the server
   * that are not warnings or errors.
   */
  INFORMATIONAL(MSGID_ERROR_SEVERITY_INFORMATIONAL),



  /**
   * The error log severity that will be used for messages that
   * provide information about mild (recoverable) errors encountered
   * during processing.
   */
  MILD_ERROR(MSGID_ERROR_SEVERITY_MILD_ERROR),



  /**
   * The error log severity that will be used for messages that
   * provide information about mild warnings triggered during
   * processing.
   */
  MILD_WARNING(MSGID_ERROR_SEVERITY_MILD_WARNING),



  /**
   * The error log severity that will be used for the most important
   * informational messages (i.e., information that should almost
   * always be logged but is not associated with a warning or error
   * condition).
   */
  NOTICE(MSGID_ERROR_SEVERITY_NOTICE),



  /**
   * The error log severity that will be used for messages that \
   * provide information about severe errors encountered during
   * processing.
   */
  SEVERE_ERROR(MSGID_ERROR_SEVERITY_SEVERE_ERROR),



  /**
   * The error log severity that will be used for messages that
   * provide information about severe warnings triggered during
   * processing.
   */
  SEVERE_WARNING(MSGID_ERROR_SEVERITY_SEVERE_WARNING),



  /**
   * The error log severity that will be used for debug messages
   * generated during server shutdown.
   */
  SHUTDOWN_DEBUG(MSGID_ERROR_SEVERITY_SHUTDOWN_DEBUG),



  /**
   * The error log severity that will be used for debug messages
   * generated during server startup.
   */
  STARTUP_DEBUG(MSGID_ERROR_SEVERITY_STARTUP_DEBUG);



  // The static hash mapping severity names to their associated
  // severity.
  private static HashMap<String,ErrorLogSeverity> nameMap;

  // The unique identifier for this error log severity.
  private int severityID;

  // The short human-readable name for this error log severity.
  private String severityName;



  static
  {
    nameMap = new HashMap<String,ErrorLogSeverity>(10);
    nameMap.put(ERROR_SEVERITY_DEBUG, GENERIC_DEBUG);
    nameMap.put(ERROR_SEVERITY_FATAL, FATAL_ERROR);
    nameMap.put(ERROR_SEVERITY_INFORMATIONAL, INFORMATIONAL);
    nameMap.put(ERROR_SEVERITY_MILD_ERROR, MILD_ERROR);
    nameMap.put(ERROR_SEVERITY_MILD_WARNING, MILD_WARNING);
    nameMap.put(ERROR_SEVERITY_NOTICE, NOTICE);
    nameMap.put(ERROR_SEVERITY_SEVERE_ERROR, SEVERE_ERROR);
    nameMap.put(ERROR_SEVERITY_SEVERE_WARNING, SEVERE_WARNING);
    nameMap.put(ERROR_SEVERITY_SHUTDOWN_DEBUG, SHUTDOWN_DEBUG);
    nameMap.put(ERROR_SEVERITY_STARTUP_DEBUG, STARTUP_DEBUG);
  }



  /**
   * Creates a new error log severity with the specified unique
   * identifier.
   *
   * @param  severityID  The unique identifier for this error log
   *                     severity.
   */
  private ErrorLogSeverity(int severityID)
  {
    this.severityID   = severityID;
    this.severityName = null;
  }



  /**
   * Retrieves the error log severity for the specified name.  The
   * name used must be the default English name for that severity.
   *
   * @param  name  The name of the error log severity to retrieve.
   *
   * @return  The error log severity for the specified name, or
   *          <CODE>null</CODE> if no such severity exists.
   */
  public static ErrorLogSeverity getByName(String name)
  {
    return nameMap.get(name);
  }



  /**
   * Retrieves the unique identifier for this error log severity.
   *
   * @return  The unique identifier for this error log severity.
   */
  public int getSeverityID()
  {
    return severityID;
  }



  /**
   * Retrieves the short human-readable name for this error log
   * severity.
   *
   * @return  The short human-readable name for this error log
   *          severity.
   */
  public String getSeverityName()
  {
    if (severityName == null)
    {
      severityName = getMessage(severityID);
    }

    return severityName;
  }



  /**
   * Retrieves a string representation of this error log severity.
   *
   * @return  A string representation of this error log severity.
   */
  public String toString()
  {
    return getSeverityName();
  }
}

