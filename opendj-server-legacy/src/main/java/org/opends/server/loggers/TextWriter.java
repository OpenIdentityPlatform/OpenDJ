/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.OutputStream;
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
  void writeRecord(String record);

  /** Flushes any buffered contents of the output stream. */
  void flush();

  /** Releases any resources held by the writer. */
  void shutdown();

  /**
   * Retrieves the number of bytes written by this writer.
   *
   * @return the number of bytes written by this writer.
   */
  long getBytesWritten();

  /** A TextWriter implementation which writes to standard out. */
  public static class STDOUT implements TextWriter
  {
    private MeteredStream stream = new MeteredStream(System.out, 0);
    private PrintWriter writer = new PrintWriter(stream, true);

    @Override
    public void writeRecord(String record)
    {
      writer.println(record);
    }

    @Override
    public void flush()
    {
      writer.flush();
    }

    @Override
    public void shutdown()
    {
      // Should never close the system out stream.
    }

    @Override
    public long getBytesWritten()
    {
      return stream.written;
    }
  }

  /** A TextWriter implementation which writes to standard error. */
  public static class STDERR implements TextWriter
  {
    private MeteredStream stream = new MeteredStream(System.err, 0);
    private PrintWriter writer = new PrintWriter(stream, true);

    @Override
    public void writeRecord(String record)
    {
      writer.println(record);
    }

    @Override
    public void flush()
    {
      writer.flush();
    }

    @Override
    public void shutdown()
    {
      // Should never close the system error stream.
    }

    @Override
    public long getBytesWritten()
    {
      return stream.written;
    }
  }

  /** A TextWriter implementation which writes to a given output stream. */
  public class STREAM implements TextWriter
  {
    private MeteredStream stream;
    private PrintWriter writer;

    /**
     * Creates a new text writer that will write to the provided output stream.
     *
     * @param  outputStream  The output stream to which
     */
    public STREAM(OutputStream outputStream)
    {
      stream = new MeteredStream(outputStream, 0);
      writer = new PrintWriter(stream, true);
    }

    @Override
    public void writeRecord(String record)
    {
      writer.println(record);
    }

    @Override
    public void flush()
    {
      writer.flush();
    }

    @Override
    public void shutdown()
    {
      // Should never close the system error stream.
    }

    @Override
    public long getBytesWritten()
    {
      return stream.written;
    }
  }
}
