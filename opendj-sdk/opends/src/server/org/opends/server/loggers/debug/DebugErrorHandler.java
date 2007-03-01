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

import org.opends.server.loggers.LogErrorHandler;
import org.opends.server.loggers.LogRecord;

/**
 * Error handler for tracing.  Tracing will be disabled
 * if too many errors occur.
 */
public class DebugErrorHandler implements LogErrorHandler
{
  private static final int ERROR_THRESHOLD= 10;

  private int _loggingErrors= 0;

  /**
   * Error handler for tracing.  Tracing will be disabled
   * if too many errors occur.
   *
   * @param record the log record that caused the error to occur.
   * @param ex the exception thrown.
   */
  public synchronized void handleError(LogRecord record, Throwable ex)
  {
    _loggingErrors++;

    DebugLogFormatter formatter = new DebugLogFormatter();
    System.err.println("Error publishing record: " +
        formatter.format(record) + ex);

    // If we've had too many write errors, just turn off
    // tracing to prevent an overflow of messages.
    if (_loggingErrors >= ERROR_THRESHOLD) {
      System.err.println("TOO MANY ERRORS FROM DEBUG LOGGER. SHUTTING DOWN");

      DebugLogger.enabled(false);
    }
  }
}
