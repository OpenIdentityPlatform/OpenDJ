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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import java.io.InputStream;
import java.io.IOException;

/**
 * An implementation of input stream that enforces an read size limit.
 */
public class SizeLimitInputStream extends InputStream
{
  private int bytesRead;
  private int markBytesRead;
  private int readLimit;
  private InputStream parentStream;

  /**
   * Creates a new a new size limit input stream.
   *
   * @param parentStream
   *          The parent stream.
   * @param readLimit
   *          The size limit.
   */
  public SizeLimitInputStream(InputStream parentStream, int readLimit)
  {
    this.parentStream = parentStream;
    this.readLimit = readLimit;
  }

  /**
   * {@inheritDoc}
   */
  public int available() throws IOException
  {
    int streamAvail = parentStream.available();
    int limitedAvail = readLimit - bytesRead;
    return limitedAvail < streamAvail ? limitedAvail : streamAvail;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void mark(int readlimit)
  {
    parentStream.mark(readlimit);
    markBytesRead = bytesRead;
  }

  /**
   * {@inheritDoc}
   */
  public int read() throws IOException
  {
    if(bytesRead >= readLimit)
    {
      return -1;
    }

    int b = parentStream.read();
    if (b != -1)
    {
      ++bytesRead;
    }
    return b;
  }

  /**
   * {@inheritDoc}
   */
  public int read(byte b[], int off, int len) throws IOException
  {
    if(off < 0 || len < 0 || off+len > b.length)
    {
      throw new IndexOutOfBoundsException();
    }

    if(len == 0)
    {
      return 0;
    }

    if(bytesRead >= readLimit)
    {
      return -1;
    }

    if(bytesRead + len > readLimit)
    {
      len = readLimit - bytesRead;
    }

    int readLen = parentStream.read(b, off, len);
    if(readLen > 0)
    {
      bytesRead += readLen;
    }
    return readLen;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void reset() throws IOException
  {
    parentStream.reset();
    bytesRead = markBytesRead;
  }

  /**
   * {@inheritDoc}
   */
  public long skip(long n) throws IOException
  {
    if(bytesRead + n > readLimit)
    {
      n = readLimit - bytesRead;
    }

    bytesRead += n;
    return parentStream.skip(n);
  }

  /**
   * {@inheritDoc}
   */
  public boolean markSupported() {
    return parentStream.markSupported();
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException {
    parentStream.close();
  }

  /**
   * Retrieves the number of bytes read from this stream.
   *
   * @return The number of bytes read from this stream.
   */
  public int getBytesRead()
  {
    return bytesRead;
  }

  /**
   * Retrieves the size limit of this stream.
   *
   * @return The size limit of this stream.
   */
  public int getSizeLimit()
  {
    return readLimit;
  }
}
