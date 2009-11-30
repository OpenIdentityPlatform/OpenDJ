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

import javax.security.sasl.SaslException;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamReaderDecorator;



/**
 * SASL stream reader.
 */
final class SASLStreamReader extends StreamReaderDecorator
{
  private final SASLFilter saslFilter;



  public SASLStreamReader(StreamReader underlyingReader,
      SASLFilter saslFilter)
  {
    super(underlyingReader);
    this.saslFilter = saslFilter;
  }



  @Override
  public boolean appendBuffer(Buffer buffer)
  {
    if (buffer == null)
    {
      return false;
    }

    byte[] appBuffer;
    try
    {
      appBuffer = saslFilter.unwrap(buffer, getConnection());
    }
    catch (SaslException e)
    {
      throw new IllegalStateException(e);
    }

    if (appBuffer.length == 0)
    {
      return false;
    }

    Buffer newBuffer = newBuffer(appBuffer.length);
    newBuffer.put(appBuffer);

    if (super.appendBuffer(newBuffer))
    {
      buffer.dispose();
      return true;
    }

    return false;
  }



  @Override
  protected Buffer read0() throws IOException
  {
    return underlyingReader.readBuffer();
  }



  @Override
  protected Buffer unwrap(Object data)
  {
    return (Buffer) data;
  }



  @Override
  protected final Object wrap(Buffer buffer)
  {
    return buffer;
  }
}
