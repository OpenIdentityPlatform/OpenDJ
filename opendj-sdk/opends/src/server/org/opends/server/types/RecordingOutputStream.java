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

import java.io.OutputStream;
import java.io.IOException;

/**
 * A wrapper OutputStream that will record all writes to an underlying
 * OutputStream. The recorded bytes will append to any previous
 * recorded bytes until the clear method is called.
 */
public class RecordingOutputStream extends OutputStream
{
  private boolean enableRecording;
  private OutputStream parentStream;
  private ByteStringBuilder buffer;

  /**
   * Constructs a new RecordingOutputStream that will all writes to
   * the given OutputStream.
   *
   * @param parentStream The output stream to record.
   */
  public RecordingOutputStream(OutputStream parentStream) {
    this.enableRecording = false;
    this.parentStream = parentStream;
    this.buffer = new ByteStringBuilder(32);
  }

  /**
   * {@inheritDoc}
   */
  public void write(int i) throws IOException {
    if(enableRecording)
    {
      buffer.append((byte) i);
    }
    parentStream.write(i);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void write(byte[] bytes) throws IOException {
    if(enableRecording)
    {
      buffer.append(bytes);
    }
    parentStream.write(bytes);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void write(byte[] bytes, int i, int i1) throws IOException {
    if(enableRecording)
    {
      buffer.append(bytes, i, i1);
    }
    parentStream.write(bytes, i, i1);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() throws IOException {
    parentStream.flush();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    parentStream.close();
  }

  /**
   * Retrieve the bytes read from this output stream since the last
   * clear.
   *
   * @return the bytes read from this output stream since the last
   *         clear.
   */
  public ByteString getRecordedBytes() {
    return buffer.toByteString();
  }

  /**
   * Clear the bytes currently recorded by this output stream.
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
   * Set whether if this output stream is recording all reads or not.
   *
   * @param enabled <code>true</code> to recording all reads or
   *                <code>false</code> otherwise.
   */
  public void setRecordingEnabled(boolean enabled)
  {
    this.enableRecording = enabled;
  }
}
