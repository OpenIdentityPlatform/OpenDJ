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
package org.opends.server.loggers;


import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

import static org.opends.server.util.ServerConstants.*;

/**
 * Print a brief summary of the LogRecord in a human readable
 * format.  The summary will typically be 1 or 2 lines.
 */
public class DirectoryFileFormatter extends Formatter
{

  private boolean auditFormat = false;



  /**
   * Creates a new directory file formatter.
   *
   * @param  audit  Indicates whether this file formatter is to be used with an
   *                audit logger.
   */
  public DirectoryFileFormatter(boolean audit)
  {
    auditFormat = audit;
  }



  /**
   * Format the given LogRecord.
   * @param record the log record to be formatted.
   * @return a formatted log record
   */
  public String format(LogRecord record)
  {
    StringBuilder sb = new StringBuilder();
    if(!auditFormat)
    {
      sb.append("[");
      sb.append(TimeThread.getLocalTime());
      sb.append("]");
      sb.append(" ");
    }
    String message = record.getMessage();
    sb.append(message);
    sb.append(EOL);
    if(!auditFormat)
    {
      if (record.getThrown() != null) {
        sb.append(StaticUtils.stackTraceToString(record.getThrown()));
      }
    }
    return sb.toString();
  }
}

