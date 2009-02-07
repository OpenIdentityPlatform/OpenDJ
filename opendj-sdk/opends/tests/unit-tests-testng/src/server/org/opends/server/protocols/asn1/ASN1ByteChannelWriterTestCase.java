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
package org.opends.server.protocols.asn1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Test class for ASN1ByteChannelWriter
 */
public class ASN1ByteChannelWriterTestCase extends ASN1WriterTestCase
{
  private ByteArrayOutputStream outStream = new ByteArrayOutputStream();
  private WritableByteChannel outChannel = Channels.newChannel(outStream);
  private ASN1Writer writer = new ASN1ByteChannelWriter(outChannel, 500);

  @Override
  ASN1Writer getWriter() throws IOException
  {
    writer.flush();
    outStream.reset();
    return writer;
  }

  @Override
  ASN1Reader getReader(byte[] encodedBytes) throws ASN1Exception, IOException
  {
    ByteArrayInputStream inStream =
        new ByteArrayInputStream(encodedBytes);
    ReadableByteChannel inChannel = Channels.newChannel(inStream);
    ASN1ByteChannelReader reader =
        new ASN1ByteChannelReader(inChannel, 500, 0);
    while(!reader.elementAvailable())
    {
      reader.processChannelData();
    }
    return reader;
  }

  @Override
  byte[] getEncodedBytes() throws IOException, ASN1Exception
  {
    writer.flush();
    return outStream.toByteArray();
  }
}
