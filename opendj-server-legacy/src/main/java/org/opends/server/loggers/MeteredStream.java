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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A metered stream is a subclass of OutputStream that
 *  (a) forwards all its output to a target stream
 *  (b) keeps track of how many bytes have been written.
 */
public final class MeteredStream extends OutputStream
{
  OutputStream out;
  volatile long written;

  /**
   * Create the stream wrapped around the specified output
   * stream.
   *
   * @param out     The target output stream to keep track of.
   * @param written The number of bytes written to the stream.
   */
  public MeteredStream(OutputStream out, long written)
  {
    this.out = out;
    this.written = written;
  }

  @Override
  public void write(int b) throws IOException
  {
    out.write(b);
    written++;
  }

  @Override
  public void write(byte buff[]) throws IOException
  {
    out.write(buff);
    written += buff.length;
  }

  @Override
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
  @Override
  public void flush() throws IOException
  {
    out.flush();
  }

  /**
   * Close the output stream which closes the target output stream.
   *
   * @exception IOException if the close failed.
   */
  @Override
  public void close() throws IOException
  {
    out.close();
  }

  /**
   * Returns the number of bytes written in this stream.
   *
   * @return the number of bytes
   */
  public long getBytesWritten()
  {
    return written;
  }
}
