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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import java.text.ParseException;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.Base64} class.
 */
public final class TestBase64 extends UtilTestCase {
  // Look up table for converting hex chars to byte values.
  private static final byte[] hexToByte = { -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1,
      -1, -1, -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
      -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1 - 1,
      -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };

  /**
   * Base 64 valid test data provider.
   * 
   * @return Returns an array of decoded and valid encoded base64 data.
   */
  @DataProvider(name = "validData")
  public Object[][] createValidData() {
    return new Object[][] {
        { "", "" },
        { "00", "AA==" },
        { "01", "AQ==" },
        { "02", "Ag==" },
        { "03", "Aw==" },
        { "04", "BA==" },
        { "05", "BQ==" },
        { "06", "Bg==" },
        { "07", "Bw==" },
        { "0000", "AAA=" },
        { "000000", "AAAA" },
        { "00000000", "AAAAAA==" },
        {
            "000102030405060708090a0b0c0d0e0f"
                + "101112131415161718191a1b1c1d1e1f"
                + "202122232425262728292a2b2c2d2e2f"
                + "303132333435363738393a3b3c3d3e3f"
                + "404142434445464748494a4b4c4d4e4f"
                + "505152535455565758595a5b5c5d5e5f"
                + "606162636465666768696a6b6c6d6e6f"
                + "707172737475767778797a7b7c7d7e7f"
                + "808182838485868788898a8b8c8d8e8f"
                + "909192939495969798999a9b9c9d9e9f"
                + "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
                + "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
                + "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf"
                + "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
                + "e0e1e2e3e4e5e6e7e8e9eaebecedeeef"
                + "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4v"
                + "MDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5f"
                + "YGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6P"
                + "kJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8vb6/"
                + "wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uvs7e7v"
                + "8PHy8/T19vf4+fr7/P3+/w==" }, };
  }

  /**
   * Base 64 invalid test data provider.
   * 
   * @return Returns an array of invalid encoded base64 data.
   */
  @DataProvider(name = "invalidData")
  public Object[][] createInvalidData() {
    // FIXME: fix cases ==== and ==x=

    return new Object[][] { { "=" }, { "==" }, { "===" }, { "A" },
        { "AA" }, { "AAA" }, { "AA`=" }, { "AA~=" }, { "AA!=" },
        { "AA@=" }, { "AA#=" }, { "AA$=" }, { "AA%=" }, { "AA^=" },
        { "AA*=" }, { "AA(=" }, { "AA)=" }, { "AA_=" }, { "AA-=" },
        { "AA{=" }, { "AA}=" }, { "AA|=" }, { "AA[=" }, { "AA]=" },
        { "AA\\=" }, { "AA;=" }, { "AA'=" }, { "AA\"=" }, { "AA:=" },
        { "AA,=" }, { "AA.=" }, { "AA<=" }, { "AA>=" }, { "AA?=" },
        { "AA;=" } };
  }

  /**
   * Tests the encode method.
   * 
   * @param hexData
   *          The decoded hex data.
   * @param encodedData
   *          The encoded data.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "validData")
  public void testEncode(String hexData, String encodedData)
      throws Exception {
    byte[] data = getBytes(hexData);
    String base64 = Base64.encode(data);
    Assert.assertEquals(base64, encodedData);
  }

  /**
   * Tests the decode method against valid data.
   * 
   * @param hexData
   *          The decoded hex data.
   * @param encodedData
   *          The encoded data.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "validData")
  public void testDecodeValidData(String hexData, String encodedData)
      throws Exception {
    byte[] data = getBytes(hexData);
    byte[] decodedData = Base64.decode(encodedData);
    Assert.assertEquals(decodedData, data);
  }

  /**
   * Tests the decode method against invalid data.
   * 
   * @param encodedData
   *          The invalid encoded data.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "invalidData", expectedExceptions = { ParseException.class })
  public void testDecodeInvalidData(String encodedData) throws Exception {
    Assert.fail("Expected exception but got result: "
        + Arrays.toString(Base64.decode(encodedData)));
  }

  /**
   * Decode a hex string to a byte-array.
   * 
   * @param hexData
   *          The string of hex.
   * @return Returns the decoded byte array.
   */
  private byte[] getBytes(String hexData) {
    int sz = hexData.length();

    if ((sz % 2) != 0) {
      throw new IllegalArgumentException(
          "Hex string does not contain an even number of hex digits");
    }

    byte[] bytes = new byte[sz / 2];

    for (int i = 0, j = 0; i < sz; i += 2, j++) {
      int c = hexData.codePointAt(i);
      if ((c & 0x7f) != c) {
        throw new IllegalArgumentException(
            "Hex string contains non-hex digits");
      }

      byte b1 = hexToByte[c];
      if (b1 < 0) {
        throw new IllegalArgumentException(
            "Hex string contains non-hex digits");
      }

      c = hexData.codePointAt(i + 1);
      if ((c & 0x7f) != c) {
        throw new IllegalArgumentException(
            "Hex string contains non-hex digits");
      }

      byte b2 = hexToByte[c];
      if (b2 < 0) {
        throw new IllegalArgumentException(
            "Hex string contains non-hex digits");
      }

      bytes[j] = (byte) ((b1 << 4) | b2);
    }

    return bytes;
  }
}
