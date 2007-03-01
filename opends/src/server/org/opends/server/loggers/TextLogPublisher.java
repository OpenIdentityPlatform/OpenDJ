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
package org.opends.server.loggers;

import org.opends.server.api.LogPublisher;

/**
 * A TextLogPublisher publishes log records to a character-based output device,
 * such as the console or a file.
 */
public class TextLogPublisher implements LogPublisher
{
  private TextWriter writer;
  private TextLogFormatter formatter;

  /**
   * Create a new TextLogPublisher using the specified destination
   * and format.
   *
   * @param writer - the destination for text output
   * @param formatter - the formatting to use to convert records to text
   */
  public TextLogPublisher(TextWriter writer, TextLogFormatter formatter)
  {
    this.writer= writer;
    this.formatter= formatter;
  }

  /**
   * Publish the log record.
   *
   * @param record the log record to publish.
   * @param handler the error handler to use if an error occurs.
   */
  public void publish(LogRecord record, LogErrorHandler handler)
  {
    try
    {
      writer.writeRecord(formatter.format(record));
    }
    catch(Throwable t)
    {
      if(handler != null)
      {
        handler.handleError(record, t);
      }
    }
  }

  /**
   * Shutdown the publisher.
   */
  public void shutdown()
  {
    writer.shutdown();
  }
}
