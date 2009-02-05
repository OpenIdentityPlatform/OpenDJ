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

package org.opends.server.types;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * Test case for ByteStringBuilder.
 */
public class ByteStringBuilderTest extends ByteSequenceTest
{
  private static final byte[] eightBytes =
      new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
          (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 };

  /**
   * ByteSequence data provider.
   *
   * @return The array of ByteStrings and the bytes it should contain.
   */
  @DataProvider(name = "byteSequenceProvider")
  public Object[][] byteSequenceProvider() throws Exception {
    Object[][] builders = byteStringBuilderProvider();
    Object[][] addlSequences = new Object[builders.length+1][];
    System.arraycopy(builders, 0, addlSequences, 0, builders.length);
    addlSequences[builders.length] = new Object[]
    { new ByteStringBuilder().append(eightBytes).subSequence(2, 6),
          new byte[]{ (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06 } };

    return addlSequences;
  }

  @DataProvider(name = "builderProvider")
  private Object[][] byteStringBuilderProvider() throws Exception
  {
    ByteBuffer testBuffer = ByteBuffer.wrap(eightBytes);
    ByteString testByteString = ByteString.wrap(eightBytes);
    ByteSequenceReader testByteReader = testByteString.asReader();
    InputStream testStream = new ByteArrayInputStream(eightBytes);
    ByteStringBuilder testBuilderFromStream = new ByteStringBuilder(8);
    testBuilderFromStream.append(testStream, 8);

    return new Object[][]
    {
      { new ByteStringBuilder().append((byte) 0x00).append((byte) 0x01),
          new byte[]{ (byte) 0x00, (byte) 0x01 } },
      { new ByteStringBuilder(5).append(new byte[]{ (byte) 0x01, (byte) 0x02,
          (byte) 0x03, (byte) 0x04 }).append(new byte[]{ (byte) 0x05,
          (byte) 0x06, (byte) 0x07, (byte) 0x08 }) , eightBytes },
      { new ByteStringBuilder(3).append(eightBytes, 0, 3).append(
          eightBytes, 3, 5), eightBytes },
      { new ByteStringBuilder().append(testBuffer, 3).append(testBuffer, 5),
           eightBytes },
      { new ByteStringBuilder(2).append(testByteString), eightBytes },
      { new ByteStringBuilder().append(testByteReader, 5).append(
          testByteReader, 3), eightBytes },
      { testBuilderFromStream, eightBytes },
      { new ByteStringBuilder().append(Short.MIN_VALUE).append(Short.MAX_VALUE),
           new byte[]{(byte) 0x80, (byte) 0x00, (byte) 0x7F, (byte) 0xFF } },
      { new ByteStringBuilder(5).append(
          Integer.MIN_VALUE).append(Integer.MAX_VALUE),
           new byte[]{(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
               (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
      { new ByteStringBuilder().append(
          Long.MIN_VALUE).append(Long.MAX_VALUE),
           new byte[]{(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
               (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x7F,
               (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
               (byte) 0xFF, (byte) 0xFF } },
      { new ByteStringBuilder(11).append("this is a").append(" test"),
           "this is a test".getBytes("UTF-8") },
      { new ByteStringBuilder().appendBERLength(0x00000000).
          appendBERLength(0x00000001).appendBERLength(0x0000000F).
          appendBERLength(0x00000010).appendBERLength(0x0000007F).

          appendBERLength(0x000000FF).

          appendBERLength(0x00000100).appendBERLength(0x00000FFF).
          appendBERLength(0x00001000).appendBERLength(0x0000FFFF).

          appendBERLength(0x00010000).appendBERLength(0x000FFFFF).
          appendBERLength(0x00100000).appendBERLength(0x00FFFFFF).

          appendBERLength(0x01000000).appendBERLength(0x0FFFFFFF).
          appendBERLength(0x10000000).appendBERLength(0xFFFFFFFF),

          new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x0F, (byte) 0x10,
              (byte) 0x7F,

              (byte) 0x81, (byte) 0xFF,

              (byte) 0x82, (byte) 0x01, (byte) 0x00,
              (byte) 0x82, (byte) 0x0F, (byte) 0xFF, (byte) 0x82, (byte) 0x10,
              (byte) 0x00, (byte) 0x82, (byte) 0xFF, (byte) 0xFF,

              (byte) 0x83, (byte) 0x01, (byte) 0x00, (byte) 0x00,
              (byte) 0x83, (byte) 0x0F, (byte) 0xFF, (byte) 0xFF,
              (byte) 0x83, (byte) 0x10, (byte) 0x00, (byte) 0x00,
              (byte) 0x83, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,

              (byte) 0x84, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00,
              (byte) 0x84, (byte) 0x0F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0x84, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00,
              (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}},

    };
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testInvalidCapacity()
  {
    new ByteStringBuilder(-1);
  }

  @Test(dataProvider = "builderProvider",
      expectedExceptions = IndexOutOfBoundsException.class)
  public void testClear(ByteStringBuilder bs, byte[] ba)
  {
    bs.clear();
    Assert.assertEquals(bs.length(), 0);
    bs.byteAt(0);
  }

  @Test
  public void testEnsureAdditionalCapacity()
  {
    ByteStringBuilder bsb = new ByteStringBuilder(8);
    Assert.assertEquals(bsb.getBackingArray().length, 8);
    bsb.ensureAdditionalCapacity(43);
    bsb.ensureAdditionalCapacity(2);
    Assert.assertTrue(bsb.getBackingArray().length >= 43);
  }

  @Test(dataProvider = "builderProvider")
  public void testGetBackingArray(ByteStringBuilder bs, byte[] ba)
  {
    byte[] trimmedArray = new byte[bs.length()];
    System.arraycopy(bs.getBackingArray(), 0, trimmedArray, 0, bs.length());
    Assert.assertTrue(Arrays.equals(trimmedArray, ba));
  }

  @Test
  public void testTrimToSize()
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    bsb.append(eightBytes);
    Assert.assertTrue(bsb.getBackingArray().length > 8);
    bsb.trimToSize();
    Assert.assertEquals(bsb.getBackingArray().length, 8);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadOffset1()
  {
    new ByteStringBuilder().append(new byte[5], -1, 3);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadOffset2()
  {
    new ByteStringBuilder().append(new byte[5], 6, 0);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadLength1()
  {
    new ByteStringBuilder().append(new byte[5], 0, 6);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadLength2()
  {
    new ByteStringBuilder().append(new byte[5], 0, -1);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadByteBufferLength1()
  {
    new ByteStringBuilder().append(ByteBuffer.wrap(new byte[5]), -1);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadByteBufferLength2()
  {
    new ByteStringBuilder().append(ByteBuffer.wrap(new byte[5]), 6);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadByteSequenceReaderLength1()
  {
    new ByteStringBuilder().append(ByteString.wrap(new byte[5]).asReader(), -1);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadByteSequenceReaderLength2()
  {
    new ByteStringBuilder().append(ByteString.wrap(new byte[5]).asReader(), 6);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testAppendBadInputStreamLength() throws Exception
  {
    ByteArrayInputStream stream = new ByteArrayInputStream(new byte[5]);
    new ByteStringBuilder().append(stream, -1);
  }

  @Test
  public void testAppendInputStream() throws Exception
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    ByteArrayInputStream stream = new ByteArrayInputStream(new byte[5]);
    Assert.assertEquals(bsb.append(stream, 10), 5);
  }
}
