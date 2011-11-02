/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap;



import java.util.Arrays;

import org.forgerock.opendj.ldap.ByteString;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class defines a set of tests for the ByteString class.
 */
@SuppressWarnings("javadoc")
public class ByteStringTestCase extends ByteSequenceTestCase
{
  /**
   * ByteString data provider.
   *
   * @return The array of ByteStrings and the bytes it should contain.
   */
  @DataProvider(name = "byteSequenceProvider")
  public Object[][] byteSequenceProvider() throws Exception
  {
    byte[] testBytes = new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
        (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
        (byte) 0x08 };

    return new Object[][] {
        { ByteString.empty(), new byte[0] },
        { ByteString.valueOf(1),
            new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01 } },
        { ByteString.valueOf(Integer.MAX_VALUE),
            new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
        { ByteString.valueOf(Integer.MIN_VALUE),
            new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00 } },
        {
            ByteString.valueOf(Long.MAX_VALUE),
            new byte[] { (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF } },
        {
            ByteString.valueOf(Long.MIN_VALUE),
            new byte[] { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 } },
        { ByteString.valueOf("cn=testvalue"), "cn=testvalue".getBytes("UTF-8") },
        { ByteString.valueOf((Object) "cn=testvalue"), "cn=testvalue".getBytes("UTF-8") },
        { ByteString.valueOf("cn=testvalue".toCharArray()), "cn=testvalue".getBytes("UTF-8") },
        { ByteString.valueOf((Object) "cn=testvalue".toCharArray()), "cn=testvalue".getBytes("UTF-8") },
        { ByteString.valueOf(testBytes), testBytes },
        { ByteString.valueOf((Object) testBytes), testBytes },
        { ByteString.valueOf(ByteString.valueOf("cn=testvalue")), "cn=testvalue".getBytes("UTF-8") },
        { ByteString.wrap(new byte[0]), new byte[0] },
        {
            ByteString
                .wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                    (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                    (byte) 0x08 }),
            new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
        {
            ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                (byte) 0x08, (byte) 0x09, (byte) 0x10 }, 0, 8),
            new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08 } },
        {
            ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                (byte) 0x08, (byte) 0x09, (byte) 0x10 }, 1, 8),
            new byte[] { (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09 } },
        {
            ByteString.wrap(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                (byte) 0x08, (byte) 0x09, (byte) 0x10 }, 2, 8),
            new byte[] { (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06,
                (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x10 } },
        {
            ByteString.wrap(
                new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                    (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                    (byte) 0x08 }, 3, 0), new byte[0] }, };
  }



  @DataProvider(name = "byteStringIntegerProvider")
  public Object[][] byteStringIntegerProvider()
  {
    return new Object[][] { { ByteString.valueOf(0), 0 },
        { ByteString.valueOf(1), 1 },
        { ByteString.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE },
        { ByteString.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE }, };
  }



  @DataProvider(name = "byteStringLongProvider")
  public Object[][] byteStringLongProvider()
  {
    return new Object[][] { { ByteString.valueOf(0L), 0L },
        { ByteString.valueOf(1L), 1L },
        { ByteString.valueOf(Long.MAX_VALUE), Long.MAX_VALUE },
        { ByteString.valueOf(Long.MIN_VALUE), Long.MIN_VALUE } };
  }



  @DataProvider(name = "byteStringCharArrayProvider")
  public Object[][] byteStringCharArrayProvider()
  {
    return new Object[][] { { "" }, { "1" }, { "1234567890" } };
  }



  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testInvalidWrapLength()
  {
    ByteString.wrap(new byte[] { (byte) 0x00, (byte) 0x01, (byte) 0x02,
        (byte) 0x03 }, 2, 8);
  }



  @Test(dataProvider = "byteStringIntegerProvider")
  public void testToInteger(final ByteString bs, final int i)
  {
    Assert.assertEquals(bs.toInt(), i);
  }



  @Test(dataProvider = "byteStringLongProvider")
  public void testToLong(final ByteString bs, final long l)
  {
    Assert.assertEquals(bs.toLong(), l);
  }



  @Test(dataProvider = "byteStringCharArrayProvider")
  public void testFromStringToCharArray(final String s)
  {
    ByteString bs = ByteString.valueOf(s);
    Assert.assertTrue(Arrays.equals(bs.toCharArray(), s.toCharArray()));
  }



  @Test(dataProvider = "byteStringCharArrayProvider")
  public void testFromCharArrayToCharArray(final String s)
  {
    final char[] chars = s.toCharArray();
    ByteString bs = ByteString.valueOf(chars);
    Assert.assertTrue(Arrays.equals(bs.toCharArray(), chars));
  }



  @Test(dataProvider = "byteStringCharArrayProvider")
  public void testValueOfCharArray(final String s)
  {
    ByteString bs = ByteString.valueOf(s.toCharArray());
    Assert.assertEquals(bs.toString(), s);
  }



  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testUndersizedToInteger()
  {
    ByteString.wrap(new byte[] { (byte) 0x00, (byte) 0x01 }).toInt();
  }



  @Test(expectedExceptions = IndexOutOfBoundsException.class)
  public void testUndersizedToLong()
  {
    ByteString.wrap(
        new byte[] { (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03 })
        .toLong();
  }
}
