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

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.IllegalBlockingModeException;

/**
 * Test class for ASN1ByteChannelReader
 */
public class ASN1ByteChannelReaderTestCase extends ASN1ReaderTestCase
{
  ASN1Reader getReader(byte[] b, int maxElementSize) throws IOException
  {
    ByteArrayInputStream inStream = new ByteArrayInputStream(b);
    ASN1ByteChannelReader reader =
        new ASN1ByteChannelReader(Channels.newChannel(inStream),
            b.length, maxElementSize);
    reader.processChannelData();
    return reader;
  }

  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeShortArrayAsNull()
      throws Exception
  {
    super.testDecodeShortArrayAsNull();
  }

  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeShortArrayAsInteger()
      throws Exception
  {
    super.testDecodeShortArrayAsInteger();
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeShortArrayAsBoolean()
      throws Exception
  {
    super.testDecodeShortArrayAsBoolean();
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { IllegalBlockingModeException.class })
  public void testDecodeShortArrayAsOctetString()
      throws Exception
  {
    super.testDecodeShortArrayAsOctetString();
  }

  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeShortArrayAsSequence()
      throws Exception
  {
    super.testDecodeShortArrayAsSequence();
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with an array that has less bytes than indicated by the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeLengthMismatchArrayAsBoolean()
      throws Exception
  {
    super.testDecodeLengthMismatchArrayAsBoolean();
  }

  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a length mismatch.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeLengthMismatchArrayAsInteger()
      throws Exception
  {
    super.testDecodeLengthMismatchArrayAsInteger();
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using an array whose actual length doesn't match with the decoded length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { IllegalBlockingModeException.class })
  public void testDecodeLengthMismatchArrayAsOctetString()
      throws Exception
  {
    super.testDecodeLengthMismatchArrayAsOctetString();
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with an array that doesn't contain a full length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeTruncatedLengthArrayAsBoolean()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsBoolean();
  }

  /**
   * Tests the <CODE>decodeAsInteger</CODE> method that takes a byte array with
   * a truncated length array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeTruncatedLengthArrayAsInteger()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsInteger();
  }

  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an array with a truncated length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeTruncatedLengthArrayAsNull()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsNull();
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using an array that doesn't fully contain the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions  = { IllegalBlockingModeException.class })
  public void testDecodeTruncatedLengthArrayAsOctetString()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsOctetString();
  }

  /**
   * Tests the <CODE>decodeAsSequence</CODE> method that takes a byte array
   * argument with an array that doesn't fully describe the length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeTruncatedLengthArrayAsSequence()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsSequence();
  }

  /**
   * Tests to make sure a premature EOF while reading a sub sequence can be
   * detected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testDecodeSequencePrematureEof()
      throws Exception
  {
    super.testDecodeSequencePrematureEof();
  }

  /**
   * Tests the <CODE>skipElement</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { IllegalBlockingModeException.class })
  public void testSkipElementIncompleteRead()
      throws Exception
  {
    super.testSkipElementIncompleteRead();
  }
}
