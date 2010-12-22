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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.ldap;



import java.io.IOException;
import java.nio.ByteBuffer;

import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1ReaderTestCase;

import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.memory.ByteBufferWrapper;



/**
 * This class provides testcases for ASN1BufferReader.
 */
public class ASN1BufferReaderTestCase extends ASN1ReaderTestCase
{
  @Override
  protected ASN1Reader getReader(final byte[] b, final int maxElementSize)
      throws IOException
  {
    final ByteBufferWrapper buffer = new ByteBufferWrapper(ByteBuffer.wrap(b));
    final ASN1BufferReader reader = new ASN1BufferReader(maxElementSize,
        TransportFactory.getInstance().getDefaultMemoryManager());
    reader.appendBytesRead(buffer);
    return reader;
  }
}
