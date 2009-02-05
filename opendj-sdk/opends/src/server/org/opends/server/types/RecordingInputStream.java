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
package org.opends.server.types;

import java.io.InputStream;
import java.io.IOException;

/**
 * A wrapper InputStream that will record all reads from an underlying
 * InputStream. The recorded bytes will append to any previous
 * recorded bytes until the clear method is called.
 */
public class RecordingInputStream extends InputStream
{
  private boolean enableRecording;
  private InputStream parentStream;
  private ByteStringBuilder buffer;

  /**
   * Constructs a new RecordingInputStream that will record all reads
   * from the given input stream.
   *
   * @param parentStream The input stream to record.
   */
  public RecordingInputStream(InputStream parentStream)
  {
    this.enableRecording = false;
    this.parentStream = parentStream;
    this.buffer = new ByteStringBuilder(32);
  }

  /**
   * {@inheritDoc}
   */
  public int read() throws IOException {
    int readByte = parentStream.read();
    if(enableRecording)
    {
      buffer.append((byte)readByte);
    }
    return readByte;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] bytes) throws IOException {
    int bytesRead = parentStream.read(bytes);
    if(enableRecording)
    {
      buffer.append(bytes, 0, bytesRead);
    }
    return bytesRead;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] bytes, int i, int i1) throws IOException {
    int bytesRead = parentStream.read(bytes, i, i1);
    if(enableRecording)
    {
      buffer.append(bytes, i, bytesRead);
    }
    return bytesRead;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long skip(long l) throws IOException {
    return parentStream.skip(l);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int available() throws IOException {
    return parentStream.available();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    parentStream.close();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void mark(int i) {
    parentStream.mark(i);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() throws IOException {
    parentStream.reset();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean markSupported() {
    return parentStream.markSupported();
  }

  /**
   * Retrieve the bytes read from this input stream since the last
   * clear.
   *
   * @return the bytes read from this input stream since the last
   *         clear.
   */
  public ByteString getRecordedBytes() {
    return buffer.toByteString();
  }

  /**
   * Clear the bytes currently recorded by this input stream.
   */
  public void clearRecordedBytes() {
    buffer.clear();
  }

  /**
   * Retrieves whether recording is enabled.
   *
   * @return whether recording is enabled.
   */
  public boolean isRecordingEnabled()
  {
    return enableRecording;
  }

  /**
   * Set whether if this input stream is recording all reads or not.
   *
   * @param enabled <code>true</code> to recording all reads or
   *                <code>false</code> otherwise.
   */
  public void setRecordingEnabled(boolean enabled)
  {
    this.enableRecording = enabled;
  }
}
