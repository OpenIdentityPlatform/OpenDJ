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

import java.util.Arrays;
import java.io.ByteArrayOutputStream;

/**
 * This class defines a set of tests for the
 * org.opends.server.types.ByteString class.
 */
public class ByteStringTest extends ByteSequenceTest
{
  @DataProvider(name = "byteStringIntegerProvier")
  public Object[][] byteStringIntegerProvider()
  {
    return new Object[][]
        {
            { ByteString.valueOf(0), 0 },
            { ByteString.valueOf(1), 1 },
            { ByteString.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE },
            { ByteString.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE },
        };
  }

  @DataProvider(name = "byteStringLongProvier")
  public Object[][] byteStringLongProvider()
  {
    return new Object[][]
        {
            { ByteString.valueOf(0L), 0L },
            { ByteString.valueOf(1L), 1L },
            { ByteString.valueOf(Long.MAX_VALUE), Long.MAX_VALUE },
            { ByteString.valueOf(Long.MIN_VALUE), Long.MIN_VALUE }
        };
  }

  /**
   * ByteString data provider.
   *
   * @return The array of ByteStrings and the bytes it should contain.
   */
  @DataProvider(name = "byteSequenceProvider")
  public Object[][] byteSequenceProvider() throws Exception
  {
    return new Object[][]
    {
        { ByteString.empty(), new byte[0] },
        { ByteString.valueOf(1), new byte[]{ (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x01 } },
        { ByteString.valueOf(Integer.MAX_VALUE), new byte[]{ (byte)0x7F,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF} },
        { ByteString.valueOf(Integer.MIN_VALUE), new byte[]{ (byte) 0x80,
            (byte) 0x00, (byte) 0x00, (byte) 0x00} },
        { ByteString.valueOf(Long.MAX_VALUE), new byte[]{ (byte) 0x7F,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF} },
        { ByteString.valueOf(Long.MIN_VALUE), new byte[]{ (byte) 0x80,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00 } },
        { ByteString.valueOf("cn=testvalue"),
            "cn=testvalue".getBytes("UTF-8") },
        { ByteString.wrap(new byte[0]), new byte[0] },
        { ByteString.wrap(new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 }),
            new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
        { ByteString.wrap(new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
            (byte) 0x09, (byte) 0x10 }, 0, 8),
            new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
        { ByteString.wrap(new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
            (byte) 0x09, (byte) 0x10 }, 1, 8),
            new byte[]{ (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09 } },
        { ByteString.wrap(new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
            (byte) 0x09, (byte) 0x10 }, 2, 8),
            new byte[]{ (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06,
                (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x10 } },
        { ByteString.wrap(new byte[]{ (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 }
            , 3, 0), new byte[0] },
    };
  }
 
  @Test(dataProvider = "byteStringIntegerProvider")
  public void testToInteger(ByteString bs, int i)
  {
    Assert.assertEquals(bs.toInt(), i);
  }

  @Test(dataProvider = "byteStringLongProvider")
  public void testToLong(ByteString bs, long l)
  {
    Assert.assertEquals(bs.toLong(), l);
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testUndersizedToInteger()
  {
    ByteString.wrap(new byte[]{(byte)0x00, (byte)0x01}).toInt();
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testUndersizedToLong()
  {
    ByteString.wrap(new byte[]{(byte)0x00, (byte)0x01, (byte)0x02,
        (byte)0x03}).toLong();
  }

  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testInvalidWrapLength()
  {
    ByteString.wrap(new byte[]{(byte)0x00, (byte)0x01, (byte)0x02,
        (byte)0x03}, 2, 8);
  }
}
