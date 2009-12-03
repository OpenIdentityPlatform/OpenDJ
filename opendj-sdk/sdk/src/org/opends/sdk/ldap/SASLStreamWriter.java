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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldap;



import java.io.IOException;
import java.util.concurrent.Future;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.streams.StreamWriterDecorator;



/**
 * SASL stream writer.
 */
@SuppressWarnings("unchecked")
final class SASLStreamWriter extends StreamWriterDecorator
{
  private final SASLFilter saslFilter;



  public SASLStreamWriter(StreamWriter underlyingWriter,
      SASLFilter saslFilter)
  {
    super(underlyingWriter);
    this.saslFilter = saslFilter;
  }



  @Override
  protected Future<Integer> flush0(Buffer buffer,
      CompletionHandler<Integer> completionHandler) throws IOException
  {
    Future<Integer> lastWriterFuture = null;

    if (buffer != null)
    {
      buffer.flip();

      Buffer underlyingBuffer = underlyingWriter.getBuffer();
      byte[] netBuffer = saslFilter.wrap(buffer, getConnection());
      int remaining = netBuffer.length;
      while (remaining > 0)
      {
        int writeSize = Math.min(remaining, underlyingBuffer
            .remaining());
        underlyingBuffer.put(netBuffer, netBuffer.length - remaining,
            writeSize);
        lastWriterFuture = underlyingWriter.flush();
        remaining -= writeSize;
      }
      buffer.clear();
    }

    return lastWriterFuture;
  }
}
