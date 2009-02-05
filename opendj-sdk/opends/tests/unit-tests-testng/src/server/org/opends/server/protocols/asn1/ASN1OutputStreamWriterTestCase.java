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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.ByteString;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import org.opends.server.util.StaticUtils;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * Test class for ASN1OutputStreamWriter
 */
public class ASN1OutputStreamWriterTestCase extends ASN1WriterTestCase
{
  ByteArrayOutputStream outStream = new ByteArrayOutputStream();
  ASN1Writer writer = new ASN1OutputStreamWriter(outStream);

  ASN1Writer getWriter()
  {
    outStream.reset();
    return writer;
  }

  ASN1Reader getReader(byte[] encodedBytes)
  {
    ByteArrayInputStream inStream =
        new ByteArrayInputStream(encodedBytes);
    return new ASN1InputStreamReader(inStream, 0);
  }

  byte[] getEncodedBytes()
  {
    return outStream.toByteArray();
  }
}
