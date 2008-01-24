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
 *      Portions Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;
import org.opends.messages.Message;

import org.opends.server.types.DN;

import static org.opends.messages.LoggerMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;

/**
 * A LogPublisherErrorHandler is used for notification of exceptions which
 * occur during the publishing of a record.
 *
 * The advantage of using a handler is that we can handle exceptions
 * asynchronously (useful when dealing with an AsynchronousPublisher).
 */
public class LogPublisherErrorHandler
{
  private DN publisherConfigDN;
  private boolean writeErroroccurred = false;

  /**
   * Construct a new log publisher error handler for a log publisher
   * with the provided configuration DN.
   *
   * @param publisherConfigDN The DN of the managed object for the
   * log publisher.
   */
  public LogPublisherErrorHandler(DN publisherConfigDN)
  {
    this.publisherConfigDN = publisherConfigDN;
  }

  /**
   * Handle an exception which occurred during the publishing
   * of a log record.
   * @param record - the record which was being published.
   * @param ex - the exception occurred.
   */
  public void handleWriteError(String record, Throwable ex)
  {
    if(!writeErroroccurred)
    {
      Message msg = ERR_LOGGER_ERROR_WRITING_RECORD.get(
              publisherConfigDN.toString(),
              stackTraceToSingleLineString(ex));
      System.err.println(msg);
      writeErroroccurred = true;
    }
  }

  /**
   * Handle an exception which occurred while trying to open a log
   * file.
   * @param file - the file which was being opened.
   * @param ex - the exception occurred.
   */
  public void handleOpenError(File file, Throwable ex)
  {
    Message msg = ERR_LOGGER_ERROR_OPENING_FILE.get(file.toString(),
                            publisherConfigDN.toString(),
                            stackTraceToSingleLineString(ex));
    System.err.println(msg);
  }

  /**
   * Handle an exception which occurred while trying to close a log
   * file.
   * @param ex - the exception occurred.
   */
  public void handleCloseError(Throwable ex)
  {
    Message msg = ERR_LOGGER_ERROR_CLOSING_FILE.get(
            publisherConfigDN.toString(),
            stackTraceToSingleLineString(ex));
    System.err.println(msg);
  }

  /**
   * Handle an exception which occurred while trying to flush the
   * writer buffer.
   * @param ex - the exception occurred.
   */
  public void handleFlushError(Throwable ex)
  {
    Message msg = ERR_LOGGER_ERROR_FLUSHING_BUFFER.get(
            publisherConfigDN.toString(),
            stackTraceToSingleLineString(ex));
    System.err.println(msg);
  }

  /**
   * Handle an exception which occured while trying to list log files
   * in a directory.
   * @param retentionPolicy - the retention policy being enforced when
   *                          the exception occured.
   * @param ex - the exception occurred.
   */
  public void handleDeleteError(RetentionPolicy retentionPolicy, Throwable ex)
  {
    Message msg = ERR_LOGGER_ERROR_ENFORCING_RETENTION_POLICY.get(
            retentionPolicy.toString(), publisherConfigDN.toString(),
            stackTraceToSingleLineString(ex));
    System.err.println(msg);
  }
}
