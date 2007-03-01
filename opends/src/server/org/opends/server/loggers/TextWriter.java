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
package org.opends.server.loggers;

import java.io.PrintWriter;

/**
 * A TextWriter provides a character-based stream used by a
 * TextLogPublisher as a target for outputting log records.
 * Separating this from a LogPublisher implementation allows
 * better sharing of targets such as the console, since a
 * TextWriter imposes no format.
 */
public class TextWriter
{
  /**
   * A TextWriter which writes to standard out.
   */
  public static TextWriter STDOUT=
      new TextWriter(new PrintWriter(System.out, true), false);
  /**
   * A TextWriter which writes to standard error.
   */
  public static TextWriter STDERR=
      new TextWriter(new PrintWriter(System.err, true), false);

  /** The underlying output stream. */
  protected PrintWriter writer;

  /** Indicates whether we should close the stream on shutdown. */
  private boolean closable;

  /**
   * Create a new TextWriter for a specified writer.
   * On shutdown, the writer will be closed.
   *
   * @param writer - a character stream used for output.
   */
  public TextWriter(PrintWriter writer)
  {
    this(writer, true);
  }

  /**
   * Create a new TextWriter for a specified writer.
   * On shutdown, the writer will be closed if requested.
   *
   * @param writer - a character stream used for output.
   * @param closeOnShutdown - indicates whether the provided.
   * stream should be closed when shutdown is invoked.
   */
  public TextWriter(PrintWriter writer, boolean closeOnShutdown)
  {
    this.writer = writer;
    closable = closeOnShutdown;
  }

  /**
   * Writes a text record to the output stream.
   *
   * @param record - the record to write.
   */
  public void writeRecord(String record)
  {
    writer.println(record);
  }

  /**
   * Flushes any buffered contents of the output stream.
   */
  public void flush()
  {
    writer.flush();
  }

  /**
   * Releases any resources held by the writer.
   * Unless <b>closeOnShutdown</b> was <b>false</b> when the writer
   * was constructed, the wrapped output stream will also be
   * closed.
   */
  public void shutdown()
  {
    // Close only if we were told to
    if (closable) {
      writer.close();
    }
  }
}
