/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.IllegalBlockingModeException;

import org.forgerock.opendj.io.ASN1Reader;
import org.testng.annotations.Test;

/** Test class for ASN1ByteChannelReader. */
public class ASN1ByteChannelReaderTestCase extends ASN1ReaderTestCase
{
  @Override
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testDecodeShortArrayAsInteger()
      throws Exception
  {
    super.testDecodeShortArrayAsInteger();
  }

  /**
   * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
   * a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testDecodeShortArrayAsEnumerated()
      throws Exception
  {
    super.testDecodeShortArrayAsEnumerated();
  }

  /**
   * Tests the <CODE>decodeAsBoolean</CODE> method that takes a byte array
   * argument with a short array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testDecodeLengthMismatchArrayAsInteger()
      throws Exception
  {
    super.testDecodeLengthMismatchArrayAsInteger();
  }

  /**
   * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
   * a length mismatch.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testDecodeLengthMismatchArrayAsEnumerated()
      throws Exception
  {
    super.testDecodeLengthMismatchArrayAsEnumerated();
  }

  /**
   * Tests the <CODE>decodeAsOctetString</CODE> method that takes a byte array
   * using an array whose actual length doesn't match with the decoded length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testDecodeTruncatedLengthArrayAsInteger()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsInteger();
  }

  /**
   * Tests the <CODE>readEnumerated</CODE> method that takes a byte array with
   * a truncated length array.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testDecodeTruncatedLengthArrayAsEnumerated()
      throws Exception
  {
    super.testDecodeTruncatedLengthArrayAsEnumerated();
  }

  /**
   * Tests the <CODE>decodeAsNull</CODE> method that takes a byte array argument
   * with an array with a truncated length.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
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
  @Override
  @Test(expectedExceptions = IllegalBlockingModeException.class)
  public void testSkipElementIncompleteRead()
      throws Exception
  {
    super.testSkipElementIncompleteRead();
  }
}
