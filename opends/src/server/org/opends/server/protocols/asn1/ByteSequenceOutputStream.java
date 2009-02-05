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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;

import org.opends.server.types.ByteStringBuilder;

import java.io.OutputStream;
import java.io.IOException;

/**
 * An adapter class that allows writing to an byte string builder
 * with the outputstream interface.
 */
final class ByteSequenceOutputStream extends OutputStream {

  private final ByteStringBuilder buffer;

  /**
   * Creates a new byte string builder output stream.
   *
   * @param buffer
   *          The underlying byte string builder.
   */
  ByteSequenceOutputStream(ByteStringBuilder buffer)
  {
    this.buffer = buffer;
  }

  /**
   * {@inheritDoc}
   */
  public void write(int i) throws IOException {
    buffer.append(((byte) (i & 0xFF)));
  }

  /**
   * {@inheritDoc}
   */
  public void write(byte[] bytes) throws IOException {
    buffer.append(bytes);
  }

  /**
   * {@inheritDoc}
   */
  public void write(byte[] bytes, int i, int i1) throws IOException {
    buffer.append(bytes, i, i1);
  }

  /**
   * Gets the length of the underlying byte string builder.
   *
   * @return The length of the underlying byte string builder.
   */
  int length() {
    return buffer.length();
  }



  /**
   * Writes the content of the underlying byte string builder to the
   * provided output stream.
   *
   * @param stream
   *          The output stream.
   * @throws IOException
   *           If an I/O error occurs. In particular, an {@code
   *           IOException} is thrown if the output stream is closed.
   */
  void writeTo(OutputStream stream) throws IOException
  {
    buffer.copyTo(stream);
  }

  /**
   * Resets this output stream such that the underlying byte string
   * builder is empty.
   */
  void reset()
  {
    buffer.clear();
  }

  /**
   * {@inheritDoc}
   */
  public void close() throws IOException {
    buffer.clear();
  }
}
