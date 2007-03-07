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
package org.opends.server.loggers.debug;

import org.opends.server.loggers.TextLogFormatter;
import org.opends.server.loggers.LogRecord;

import java.util.Locale;
import java.util.Map;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * TraceFormatter defines the output format of trace messages.
 */
public class DebugLogFormatter implements TextLogFormatter
{

  private DateFormat _timestamper;

  /**
   * Construct a new DebugLogFormatter.
   */
  public DebugLogFormatter()
  {
    _timestamper= new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS", Locale.US);
  }

  /**
   * Format the log record. Only DebugLogRecords will be formated correctly.
   *
   * @param record the record to format.
   * @return the recrod formated to a string.
   */
  public String format(LogRecord record)
  {
    StringBuffer buf = new StringBuffer();
    if(record != null && record instanceof DebugLogRecord)
    {
      DebugLogRecord dlr = (DebugLogRecord)record;

      // Emit the timestamp.
      buf.append(_timestamper.format(dlr.getTimestamp()));
      buf.append(" ");

      // Emit the seq num
      buf.append(dlr.getSequenceNumber());
      buf.append(" ");

      // Emit debug category.
      buf.append(dlr.getCategory());
      buf.append(" ");

      // Emit the debug level.
      buf.append(dlr.getLevel());
      buf.append(" ");

      // Emit thread info.
      buf.append("thread={");
      buf.append(dlr.getThreadName());
      buf.append("(");
      buf.append(dlr.getThreadID());
      buf.append(")} ");

      if(dlr.getThreadProperties() != null)
      {
        buf.append("threadDetail={");
        for(Map.Entry entry : dlr.getThreadProperties().entrySet())
        {
          buf.append(entry.getKey());
          buf.append("=");
          buf.append(entry.getValue());
          buf.append(" ");
        }
        buf.append("} ");
      }

      // Emit method info.
      buf.append("signature={");
      buf.append(dlr.getSignature());
      buf.append(" @ ");
      buf.append(dlr.getSourceLocation());
      buf.append("} ");

      // Emit message.
      buf.append(dlr.getMessage());

      // Emit Stack Trace.
      if(dlr.getStackTrace() != null)
      {
        buf.append("\nStack Trace:\n");
        buf.append(dlr.getStackTrace());
      }

    }

    return buf.toString();
  }
}
