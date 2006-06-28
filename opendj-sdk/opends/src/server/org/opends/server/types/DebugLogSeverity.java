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
package org.opends.server.types;



import java.util.HashMap;

import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This enumeration defines the set of severity levels that may be
 * used when writing a message to a debug logger.
 */
public enum DebugLogSeverity
{
  /**
   * The severity level that will be used for highly verbose debug
   * information in cases that do not indicate any kind of error
   * condition.
   */
  VERBOSE(MSGID_DEBUG_SEVERITY_VERBOSE),



  /**
   * The severity level that will be used for providing informational
   * notices for use when debugging the server.
   */
  INFO(MSGID_DEBUG_SEVERITY_INFO),



  /**
   * The severity level that will be used for providing messages that
   * may offer additional information about a warning.
   */
  WARNING(MSGID_DEBUG_SEVERITY_WARNING),



  /**
   * The severity level that will be used for providing messages that
   * may offer additional information about an error.
   */
  ERROR(MSGID_DEBUG_SEVERITY_ERROR),



  /**
   * The severity level that will be used for debug information about
   * the communication between the server and its clients.
   */
  COMMUNICATION(MSGID_DEBUG_SEVERITY_COMMUNICATION);



  // The static hash mapping severity names to their associated
  // severity.
  private static HashMap<String,DebugLogSeverity> nameMap;

  // The unique identifier assigned to this debug log severity.
  int severityID;

  // The short human-readable name of this debug log severity.
  String severityName;



  static
  {
    nameMap = new HashMap<String,DebugLogSeverity>(5);
    nameMap.put(DEBUG_SEVERITY_COMMUNICATION, COMMUNICATION);
    nameMap.put(DEBUG_SEVERITY_ERROR, ERROR);
    nameMap.put(DEBUG_SEVERITY_INFO, INFO);
    nameMap.put(DEBUG_SEVERITY_VERBOSE, VERBOSE);
    nameMap.put(DEBUG_SEVERITY_WARNING, WARNING);
  }



  /**
   * Creates a new debug log severity with the specified ID.
   *
   * @param  severityID  The unique identifier assigned to this debug
   *                     log severity.
   */
  private DebugLogSeverity(int severityID)
  {
    this.severityID   = severityID;
    this.severityName = null;
  }



  /**
   * Retrieves the debug log severity for the specified name.  The
   * name used must be the default English name for that severity.
   *
   * @param  name  The name of the debug log severity to retrieve.
   *
   * @return  The debug log severity for the specified name, or
   *          <CODE>null</CODE> if no such severity exists.
   */
  public static DebugLogSeverity getByName(String name)
  {
    return nameMap.get(name);
  }



  /**
   * Retrieves the unique identifier assigned to this debug log
   * severity.
   *
   * @return  The unique identifier assigned to this debug log
   *          severity.
   */
  public int getSeverityID()
  {
    return severityID;
  }



  /**
   * Retrieves the human-readable name of this debug log severity.
   *
   * @return  The human-readable name of this debug log severity.
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
   * Retrieves a string representation of this debug log severity.
   *
   * @return  A string representation of this debug log severity.
   */
  public String toString()
  {
    return getSeverityName();
  }
}

