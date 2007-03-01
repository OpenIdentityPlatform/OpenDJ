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

import java.io.IOException;
import java.io.OutputStream;

/**
 * A metered stream is a subclass of OutputStream that
 *  (a) forwards all its output to a target stream
 *  (b) keeps track of how many bytes have been written.
 */
class MeteredStream extends OutputStream
{
  OutputStream out;
  long written;

  /**
   * Create the stream wrapped around the specified output
   * stream.
   *
   * @param out     The target output stream to keep track of.
   * @param written The number of bytes written to the stream.
   */
  MeteredStream(OutputStream out, long written)
  {
    this.out = out;
    this.written = written;
  }

  /**
   * Write the specified byte to the stream.
   *
   * @param b The value to be written to the stream.
   *
   * @exception IOException if the write failed.
   */
  public void write(int b) throws IOException
  {
    out.write(b);
    written++;
  }

  /**
   * Write the specified buffer to the stream.
   *
   * @param buff The value to be written to the stream.
   *
   * @exception IOException if the write failed.
   */
  public void write(byte buff[]) throws IOException
  {
    out.write(buff);
    written += buff.length;
  }

  /**
   * Write the specified buffer to the stream.
   *
   * @param buff The value to be written to the stream.
   * @param off  The offset to write from.
   * @param len  The length of the buffer to write.
   *
   * @exception IOException if the write failed.
   */
  public void write(byte buff[], int off, int len) throws IOException
  {
    out.write(buff,off,len);
    written += len;
  }

  /**
   * Flush the output stream which flushes the target output stream.
   *
   * @exception IOException if the flush failed.
   */
  public void flush() throws IOException
  {
    out.flush();
  }

  /**
   * Close the output stream which closes the target output stream.
   *
   * @exception IOException if the close failed.
   */
  public void close() throws IOException
  {
    out.close();
  }
}

