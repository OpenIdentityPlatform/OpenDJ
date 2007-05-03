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

import java.io.PrintWriter;

/**
 * A TextWriter provides a character-based stream used by a
 * Text Publishers as a target for outputting log records.
 */
public interface TextWriter
{
  /**
   * Writes a text record to the output stream.
   *
   * @param record - the record to write.
   */
  public void writeRecord(String record);

  /**
   * Flushes any buffered contents of the output stream.
   */
  public void flush();

  /**
   * Releases any resources held by the writer.
   */
  public void shutdown();

  /**
   * Retrieves the number of bytes written by this writer.
   *
   * @return the number of bytes written by this writer.
   */
  public long getBytesWritten();

  /**
   * A TextWriter implementationwhich writes to standard out.
   */
  public static class STDOUT implements TextWriter
  {
    private MeteredStream stream = new MeteredStream(System.out, 0);
    private PrintWriter writer = new PrintWriter(stream, true);

    /**
     * {@inheritDoc}
     */
    public void writeRecord(String record)
    {
      writer.println(record);
    }

    /**
     * {@inheritDoc}
     */
    public void flush()
    {
      writer.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown()
    {
      writer.close();
    }

    /**
     * {@inheritDoc}
     */
    public long getBytesWritten()
    {
      return stream.written;
    }
  }

  /**
   * A TextWriter implementation which writes to standard error.
   */
  public static class STDERR implements TextWriter
  {
    private MeteredStream stream = new MeteredStream(System.err, 0);
    private PrintWriter writer = new PrintWriter(stream, true);

    /**
     * {@inheritDoc}
     */
    public void writeRecord(String record)
    {
      writer.println(record);
    }

    /**
     * {@inheritDoc}
     */
    public void flush()
    {
      writer.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown()
    {
      writer.close();
    }

    /**
     * {@inheritDoc}
     */
    public long getBytesWritten()
    {
      return stream.written;
    }
  }
}
