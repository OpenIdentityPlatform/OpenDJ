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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.StaticUtils} class.
 */
public final class TestStaticUtils extends UtilTestCase {
  // Lower case hex digit lookup table.
  private static final char[] HEX_DIGITS_LOWER = new char[] { '0', '1',
      '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

  // Upper case hex digit lookup table.
  private static final char[] HEX_DIGITS_UPPER = new char[] { '0', '1',
      '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

  // Lookup table for 8-bit strings.
  private static final String[] BIT_STRINGS = new String[256];
  static {
    final char[] ZEROS = new char[] { '0', '0', '0', '0', '0', '0', '0',
        '0' };

    for (int i = 0; i < 256; i++) {
      String bits = Integer.toBinaryString(i);
      StringBuilder sb = new StringBuilder(8);
      sb.append(ZEROS, 0, 8 - bits.length());
      sb.append(bits);
      BIT_STRINGS[i] = sb.toString();
    }
  }

  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public void setUp() throws Exception {
  }

  /**
   * Create test strings for the {@link StaticUtils#getBytes(String)}.
   *
   * @return Returns an array of test strings.
   */
  @DataProvider(name = "getBytesTestData")
  public Object[][] createGetBytesTestData() {
    List<String> strings = new LinkedList<String>();

    // Some simple strings.
    strings.add("");
    strings.add(" ");
    strings.add("an ascii string");

    // A string containing just UTF-8 1 byte sequences.
    StringBuilder builder = new StringBuilder();
    for (char c = '\u0000'; c < '\u0080'; c++) {
      builder.append(c);
    }
    strings.add(builder.toString());

    // A string containing UTF-8 1 and 2 byte sequences.
    builder = new StringBuilder();
    for (char c = '\u0000'; c < '\u0100'; c++) {
      builder.append(c);
    }
    strings.add(builder.toString());

    // A string containing UTF-8 1 and 6 byte sequences.
    builder = new StringBuilder();
    for (char c = '\u0000'; c < '\u0080'; c++) {
      builder.append(c);
    }
    for (char c = '\uff00'; c != '\u0000'; c++) {
      builder.append(c);
    }
    strings.add(builder.toString());

    // Construct the array.
    Object[][] data = new Object[strings.size()][];
    for (int i = 0; i < strings.size(); i++) {
      data[i] = new Object[] { strings.get(i) };
    }

    return data;
  }

  /**
   * Tests the {@link StaticUtils#getBytes(String)} method.
   *
   * @param inputString
   *          The input string.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "getBytesTestData")
  public void testGetBytes(String inputString) throws Exception {
    Assert.assertEquals(StaticUtils.getBytes(inputString), inputString
        .getBytes("UTF-8"));
  }

  /**
   * Tests the {@link StaticUtils#getBytes(char[])} method.
   *
   * @param inputString
   *          The input string.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "getBytesTestData")
  public void testCharsToBytes(String inputString) throws Exception {
    Assert.assertEquals(StaticUtils.getBytes(inputString.toCharArray()),
        inputString.getBytes("UTF-8"));
  }

  /**
   * Create test strings for the {@link StaticUtils#byteToHex(byte)}.
   *
   * @return Returns an array of test strings.
   */
  @DataProvider(name = "byteToHexTestData")
  public Object[][] createByteToHexTestData() {
    Object[][] data = new Object[256][];

    for (int i = 0; i < 256; i++) {
      data[i] = new Object[] { new Byte((byte) i) };
    }

    return data;
  }

  /**
   * Tests the {@link StaticUtils#byteToHex(byte)} method.
   *
   * @param b
   *          The input byte.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "byteToHexTestData")
  public void testByteToHex(byte b) throws Exception {
    char[] chars = new char[] { HEX_DIGITS_UPPER[(b & 0xf0) >> 4],
        HEX_DIGITS_UPPER[b & 0x0f] };

    String hex = new String(chars);

    Assert.assertEquals(StaticUtils.byteToHex(b), hex);
  }

  /**
   * Tests the {@link StaticUtils#byteToLowerHex(byte)} method.
   *
   * @param b
   *          The input byte.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "byteToHexTestData")
  public void testByteToLowerHex(byte b) throws Exception {
    char[] chars = new char[] { HEX_DIGITS_LOWER[(b & 0xf0) >> 4],
        HEX_DIGITS_LOWER[b & 0x0f] };

    String hex = new String(chars);

    Assert.assertEquals(StaticUtils.byteToLowerHex(b), hex);
  }

  /**
   * Tests the {@link StaticUtils#byteToASCII(byte)} method.
   *
   * @param b
   *          The input byte.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "byteToHexTestData")
  public void testByteToASCII(byte b) throws Exception {
    if (b < 32 || b > 126) {
      Assert.assertEquals(StaticUtils.byteToASCII(b), ' ');
    } else {
      Assert.assertEquals(StaticUtils.byteToASCII(b), (char) b);
    }
  }

  /**
   * Create test strings for the {@link StaticUtils#bytesToHex(byte[])}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "bytesToHexTestData")
  public Object[][] createBytesToHexTestData() {
    return new Object[][] {
        { null, "" },
        { new byte[0], "" },
        { new byte[] { 0x00 }, "00" },
        { new byte[] { 0x00, 0x7f, (byte) 0x80, (byte) 0xff },
            "00 7F 80 FF" } };
  }

  /**
   * Tests the {@link StaticUtils#bytesToHex(byte[])} method.
   *
   * @param bytes
   *          The input byte array.
   * @param expected
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "bytesToHexTestData")
  public void testBytesToHex(byte[] bytes, String expected)
      throws Exception {
    Assert.assertEquals(StaticUtils.bytesToHex(bytes), expected);
  }

  /**
   * Tests the {@link StaticUtils#bytesToHex(java.nio.ByteBuffer)}
   * method.
   *
   * @param bytes
   *          The input byte array.
   * @param expected
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "bytesToHexTestData")
  public void testBytesToHexByteBuffer(byte[] bytes, String expected)
      throws Exception {
    ByteBuffer buffer = (bytes != null) ? ByteBuffer.wrap(bytes) : null;

    Assert.assertEquals(StaticUtils.bytesToHex(buffer), expected);
  }

  /**
   * Tests the {@link StaticUtils#byteToBinary(byte)} method.
   *
   * @param b
   *          The input byte.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "byteToHexTestData")
  public void testByteToBinary(byte b) throws Exception {
    Assert.assertEquals(StaticUtils.byteToBinary(b), BIT_STRINGS[b & 0xff]);
  }

  /**
   * Create test data for {@link StaticUtils#compare(byte[], byte[])}.
   * 
   * @return Returns an array of test data.
   */
  @DataProvider(name = "compareBytesTestData")
  public Object[][] createCompareBytesTestData() {
    return new Object[][] {
        { null, null, 0 },
        { null, new byte[0], -1 },
        { new byte[0], null, 1 },
        { new byte[0], new byte[0], 0 },
        { new byte[] { 0x00 }, new byte[] { 0x00 }, 0 },
        { new byte[] { 0x01 }, new byte[] { 0x00 }, 1 },
        { new byte[] { 0x7f }, new byte[] { 0x00 }, 1 },
        { new byte[] { (byte) 0x80 }, new byte[] { 0x00 }, -1 },
        { new byte[] { (byte) 0xff }, new byte[] { 0x00 }, -1 },
        { new byte[] { 0x00 }, new byte[] { 0x01 }, -1 },
        { new byte[] { 0x00 }, new byte[] { 0x7f }, -1 },
        { new byte[] { 0x00 }, new byte[] { (byte) 0x80 }, 1 },
        { new byte[] { 0x00 }, new byte[] { (byte) 0xff }, 1 },
        { new byte[] { 0x00, 0x01, 0x02 },
            new byte[] { 0x00, 0x01, 0x02 }, 0 },
        { new byte[] { 0x00, 0x01 }, new byte[] { 0x00, 0x01, 0x02 },
            -1 },
        { new byte[] { 0x00, 0x01, 0x02 }, new byte[] { 0x00, 0x01 },
            1 }, };
  }

  /**
   * Tests the {@link StaticUtils#compare(byte[], byte[])} method.
   * 
   * @param a
   *          The first byte array.
   * @param a2
   *          The second byte array.
   * @param expected
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "compareBytesTestData")
  public void testCompareBytes(byte[] a, byte[] a2, int expected)
      throws Exception {
    int rc = StaticUtils.compare(a, a2);

    if (expected < 0 && rc >= 0) {
      Assert.fail("Expected negative result but got " + rc);
    }

    if (expected > 0 && rc <= 0) {
      Assert.fail("Expected positive result but got " + rc);
    }

    if (expected == 0 && rc != 0) {
      Assert.fail("Expected zero result but got " + rc);
    }
  }

  /**
   * Create test strings for the {@link StaticUtils#isDigit(char)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "isDigitTestData")
  public Object[][] createIsDigitTestData() {
    List<Object[]> data = new LinkedList<Object[]>();

    for (char c = '0'; c <= '9'; c++) {
      data.add(new Object[] { c, true });
    }

    data.add(new Object[] { ' ', false });
    data.add(new Object[] { (char) ('0' - 1), false });
    data.add(new Object[] { (char) ('9' + 1), false });
    data.add(new Object[] { '\uFF10', false });

    return data.toArray(new Object[2][]);
  }

  /**
   * Tests the {@link StaticUtils#isDigit(char)} method.
   *
   * @param c
   *          The test char.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isDigitTestData")
  public void testIsDigit(char c, boolean result) throws Exception {
    Assert.assertEquals(StaticUtils.isDigit(c), result);
  }

  /**
   * Create test strings for the {@link StaticUtils#isAlpha(char)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "isAlphaTestData")
  public Object[][] createIsAlphaTestData() {
    List<Object[]> data = new LinkedList<Object[]>();

    for (char c = 'a'; c <= 'z'; c++) {
      data.add(new Object[] { c, true });
    }

    for (char c = 'A'; c <= 'Z'; c++) {
      data.add(new Object[] { c, true });
    }

    for (char c = '0'; c <= '9'; c++) {
      data.add(new Object[] { c, false });
    }

    data.add(new Object[] { ' ', false });
    data.add(new Object[] { (char) ('a' - 1), false });
    data.add(new Object[] { (char) ('z' + 1), false });
    data.add(new Object[] { (char) ('A' - 1), false });
    data.add(new Object[] { (char) ('Z' + 1), false });
    data.add(new Object[] { '\u00D9', false });

    return data.toArray(new Object[2][]);
  }

  /**
   * Tests the {@link StaticUtils#isAlpha(char)} method.
   *
   * @param c
   *          The test char.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isAlphaTestData")
  public void testIsAlpha(char c, boolean result) throws Exception {
    Assert.assertEquals(StaticUtils.isAlpha(c), result);
  }

  /**
   * Create test strings for the {@link StaticUtils#isHexDigit(char)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "isHexDigitTestData")
  public Object[][] createIsHexDigitTestData() {
    List<Object[]> data = new LinkedList<Object[]>();

    for (char c = 'a'; c <= 'f'; c++) {
      data.add(new Object[] { c, true });
    }

    for (char c = 'A'; c <= 'F'; c++) {
      data.add(new Object[] { c, true });
    }

    for (char c = '0'; c <= '9'; c++) {
      data.add(new Object[] { c, true });
    }

    data.add(new Object[] { ' ', false });
    data.add(new Object[] { (char) ('0' - 1), false });
    data.add(new Object[] { (char) ('9' + 1), false });
    data.add(new Object[] { (char) ('a' - 1), false });
    data.add(new Object[] { (char) ('f' + 1), false });
    data.add(new Object[] { (char) ('A' - 1), false });
    data.add(new Object[] { (char) ('F' + 1), false });
    data.add(new Object[] { '\u00D9', false });

    return data.toArray(new Object[2][]);
  }

  /**
   * Tests the {@link StaticUtils#isHexDigit(char)} method.
   *
   * @param c
   *          The test char.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isHexDigitTestData")
  public void testIsHexDigit(char c, boolean result) throws Exception {
    Assert.assertEquals(StaticUtils.isHexDigit(c), result);
  }

  /**
   * Create invalid test strings for the
   * {@link StaticUtils#hexStringToByteArray(String)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "hexStringToByteArrayInvalidTestData")
  public Object[][] createHexStringToByteArrayInvalidTestData() {
    return new Object[][] { { "a" }, { "aaa" }, { "0/" }, { "0:" },
        { "0@" }, { "0G" }, { "0`" }, { "0g" } };
  }

  /**
   * Tests the {@link StaticUtils#hexStringToByteArray(String)} method.
   *
   * @param hexString
   *          The test string.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = ParseException.class, dataProvider = "hexStringToByteArrayInvalidTestData")
  public void testHexStringToByteArrayException(String hexString)
      throws Exception {
    StaticUtils.hexStringToByteArray(hexString);
  }

  /**
   * Create test strings for the
   * {@link StaticUtils#hexStringToByteArray(String)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "hexStringToByteArrayTestData")
  public Object[][] createHexStringToByteArrayTestData() {
    return new Object[][] { { null, new byte[0] }, { "", new byte[0] },
        { "00010f107f80ff", new byte[] { 0, 1, 15, 16, 127, -128, -1 } } };
  }

  /**
   * Tests the {@link StaticUtils#hexStringToByteArray(String)} method.
   *
   * @param hexString
   *          The test string.
   * @param bytes
   *          The expected byte array.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "hexStringToByteArrayTestData")
  public void testHexStringToByteArray(String hexString, byte[] bytes)
      throws Exception {
    Assert.assertEquals(StaticUtils.hexStringToByteArray(hexString), bytes);
  }

  /**
   * Create test strings for the
   * {@link StaticUtils#needsBase64Encoding(String)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "needsBase64EncodingTestData")
  public Object[][] createNeedsBase64EncodingTestData() {
    List<Object[]> data = new LinkedList<Object[]>();

    // Check SAFE-INIT-CHAR.
    for (char c = '\u0000'; c < '\u0100'; c++) {
      boolean result = false;

      switch (c) {
      case '\u0000':
      case '\r':
      case '\n':
      case ' ':
      case ':':
      case '<':
        result = true;
        break;
      default:
        if (c >= '\u0080') {
          result = true;
        }
        break;
      }

      String s = new String(new char[] { c, 'a', 'b', 'c' });
      data.add(new Object[] { s, result });
    }

    // Check SAFE-CHAR.
    for (char c = '\u0000'; c < '\u0100'; c++) {
      boolean result = false;

      switch (c) {
      case '\u0000':
      case '\r':
      case '\n':
        result = true;
        break;
      default:
        if (c >= '\u0080') {
          result = true;
        }
        break;
      }

      String s = new String(new char[] { 'a', 'b', c, 'c' });
      data.add(new Object[] { s, result });
    }

    data.add(new Object[] { null, false });
    data.add(new Object[] { "", false });
    data.add(new Object[] { " ", true });
    data.add(new Object[] { "abc ", true });

    return data.toArray(new Object[2][]);
  }

  /**
   * Tests the {@link StaticUtils#needsBase64Encoding(String)} method.
   *
   * @param s
   *          The test string.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "needsBase64EncodingTestData")
  public void testNeedsBase64EncodingString(String s, boolean result)
      throws Exception {
    Assert.assertEquals(StaticUtils.needsBase64Encoding(s), result);
  }

  /**
   * Tests the {@link StaticUtils#needsBase64Encoding(byte[])} method.
   *
   * @param s
   *          The test string.
   * @param result
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "needsBase64EncodingTestData")
  public void testNeedsBase64EncodingBytes(String s, boolean result)
      throws Exception {
    byte[] bytes = s != null ? s.getBytes("UTF-8") : null;
    Assert.assertEquals(StaticUtils.needsBase64Encoding(bytes), result);
  }

  /**
   * Create test strings for the
   * {@link StaticUtils#isRelativePath(String)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "isRelativePathTestData")
  public Object[][] createIsRelativePathTestData() {
    String root = File.listRoots()[0].getPath();
    return new Object[][] { { "", true }, { root, false },
         { root + "foo", false }, { "foo", true },
         { "foo" + File.separator + "bar", true },
         { root + "foo" + File.separator + "bar", false },
         { ".", true }, { "..", true },
         { root + "foo" + File.separator + ".", false },
         { root + "foo" + File.separator + "..", false } };
  }

  /**
   * Tests the {@link StaticUtils#isRelativePath(String)} method.
   *
   * @param path
   *          The test string.
   * @param result
   *          Expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "isRelativePathTestData")
  public void testIsRelativePath(String path, boolean result)
      throws Exception {
    Assert.assertEquals(StaticUtils.isRelativePath(path), result);
  }

  /**
   * Create test lists for the {@link StaticUtils#listToArray(List)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "listToArrayTestData")
  public Object[][] createListToArrayTestData() {
    return new Object[][] { { null }, { new String[] {} },
        { new String[] { "aaa" } },
        { new String[] { "aaa", "bbb", "ccc" } } };
  }

  /**
   * Tests the {@link StaticUtils#listToArray(List)} method.
   *
   * @param strings
   *          The test string list.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "listToArrayTestData")
  public void testListToArray(String[] strings) throws Exception {
    if (strings != null) {
      List<String> list = new ArrayList<String>(strings.length);
      for (String string : strings) {
        list.add(string);
      }
      Assert.assertEquals(StaticUtils.listToArray(list), strings);
    } else {
      Assert.assertNull(StaticUtils.listToArray(null));
    }
  }

  /**
   * Tests the {@link StaticUtils#moveFile(java.io.File, java.io.File)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IOException.class)
  public void testMoveFileNonExistentSrc() throws Exception {
    File src = File.createTempFile("src", null);
    File dst = TestCaseUtils.createTemporaryDirectory("dst");
    File newSrc = new File(dst, src.getName());

    src.delete();

    try {
      StaticUtils.moveFile(src, dst);
    } finally {
      src.delete();
      dst.delete();
      newSrc.delete();
    }
  }

  /**
   * Tests the {@link StaticUtils#moveFile(java.io.File, java.io.File)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IOException.class)
  public void testMoveFileNonExistentDst() throws Exception {
    File src = File.createTempFile("src", null);
    File dst = TestCaseUtils.createTemporaryDirectory("dst");
    File newSrc = new File(dst, src.getName());

    dst.delete();

    try {
      StaticUtils.moveFile(src, dst);
    } finally {
      src.delete();
      dst.delete();
      newSrc.delete();
    }
  }

  /**
   * Tests the {@link StaticUtils#moveFile(java.io.File, java.io.File)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IOException.class)
  public void testMoveFileSrcNotFile() throws Exception {
    File src = TestCaseUtils.createTemporaryDirectory("src");
    File dst = TestCaseUtils.createTemporaryDirectory("dst");
    File newSrc = new File(dst, src.getName());

    try {
      StaticUtils.moveFile(src, dst);
    } finally {
      src.delete();
      dst.delete();
      newSrc.delete();
    }
  }

  /**
   * Tests the {@link StaticUtils#moveFile(java.io.File, java.io.File)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IOException.class)
  public void testMoveFileDstNotDirectory() throws Exception {
    File src = File.createTempFile("src", null);
    File dst = File.createTempFile("dst", null);
    File newSrc = new File(dst, src.getName());

    try {
      StaticUtils.moveFile(src, dst);
    } finally {
      src.delete();
      dst.delete();
      newSrc.delete();
    }
  }

  /**
   * Create test content for {@link StaticUtils#moveFile(File, File)}.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "moveFileTestData")
  public Object[][] createMoveFileTestData() {
    return new Object[][] { { new String[] {} }, { new String[] { "" } },
        { new String[] { "", "" } }, { new String[] { " " } },
        { new String[] { " ", "", " " } },
        { new String[] { "one two three", "four five six", "seven" } } };
  }

  /**
   * Tests the {@link StaticUtils#moveFile(java.io.File, java.io.File)}
   * method.
   *
   * @param lines
   *          The test file contents.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "moveFileTestData")
  public void testMoveFile(String[] lines) throws Exception {
    File src = File.createTempFile("src", null);
    File dst = TestCaseUtils.createTemporaryDirectory("dst");
    File newSrc = new File(dst, src.getName());

    try {
      // Generate contents.
      PrintWriter writer = new PrintWriter(new BufferedWriter(
          new FileWriter(src)));

      for (String line : lines) {
        writer.println(line);
      }
      writer.close();

      // Move the file.
      StaticUtils.moveFile(src, dst);

      // Post conditions.
      Assert.assertFalse(src.exists());

      Assert.assertTrue(newSrc.exists());

      BufferedReader reader = new BufferedReader(new FileReader(newSrc));
      for (String line : lines) {
        Assert.assertEquals(reader.readLine(), line);
      }
      Assert.assertNull(reader.readLine());
      reader.close();
    } finally {
      src.delete();
      dst.delete();
      newSrc.delete();
    }
  }

  /**
   * Tests the {@link StaticUtils#recursiveDelete(File)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testRecursiveDeleteNonExistent() throws Exception {
    File src = File.createTempFile("src", null);
    src.delete();

    Assert.assertFalse(StaticUtils.recursiveDelete(src));
  }

  /**
   * Tests the {@link StaticUtils#recursiveDelete(File)} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testRecursiveDelete() throws Exception {
    File dir0 = TestCaseUtils.createTemporaryDirectory("dst");

    List<File> files = new LinkedList<File>();

    File dir1 = new File(dir0, "one");
    dir1.mkdir();
    files.add(dir1);

    File dir2 = new File(dir0, "two");
    dir2.mkdir();
    files.add(dir2);

    File dir3 = new File(dir0, "three");
    dir3.mkdir();
    files.add(dir3);

    File dir4 = new File(dir1, "four");
    dir4.mkdir();
    files.add(dir4);

    File f1 = new File(dir1, "f1");
    f1.createNewFile();
    files.add(f1);

    File f2 = new File(dir1, "f2");
    f2.createNewFile();
    files.add(f2);

    File f3 = new File(dir2, "f3");
    f3.createNewFile();
    files.add(f3);

    Assert.assertTrue(StaticUtils.recursiveDelete(dir0));

    for (File f : files) {
      Assert.assertFalse(f.exists());
    }
  }

  /**
   * Create test strings for the {@link StaticUtils#toLowerCase(String)}
   * related methods.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "stringCaseConversionTestData")
  public Object[][] createStringCaseConversionTestData() {
    return new Object[][] {
        { null, null, null },
        { "", "", "" },
        { " ", " ", " " },
        { "  a  B  c  ", "  a  b  c  ", "  A  B  C  " },
        {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789{}|[]:;'<>?,./!@#$%^&*()_+",
            "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz0123456789{}|[]:;'<>?,./!@#$%^&*()_+",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789{}|[]:;'<>?,./!@#$%^&*()_+" },
        { "some non-ascii \u00c0\u00e0\u00c6\u00e6\u00dd\u00fd",
            "some non-ascii \u00e0\u00e0\u00e6\u00e6\u00fd\u00fd",
            "SOME NON-ASCII \u00c0\u00c0\u00c6\u00c6\u00dd\u00dd" } };
  }

  /**
   * Tests the {@link StaticUtils#toLowerCase(String)} method.
   *
   * @param input
   *          The test string.
   * @param lower
   *          The test string in lower case.
   * @param upper
   *          The test string in upper case.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "stringCaseConversionTestData")
  public void testToLowerCaseString(String input, String lower, String upper)
      throws Exception {
    Assert.assertEquals(StaticUtils.toLowerCase(input), lower);
  }

  /**
   * Tests the
   * {@link StaticUtils#toLowerCase(byte[], StringBuilder, boolean)}
   * method.
   *
   * @param input
   *          The test string.
   * @param lower
   *          The test string in lower case.
   * @param upper
   *          The test string in upper case.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "stringCaseConversionTestData")
  public void testToLowerCaseBytes(String input, String lower, String upper)
      throws Exception {
    byte[] bytes = input != null ? input.getBytes("UTF-8") : null;
    StringBuilder buffer = new StringBuilder();
    StaticUtils.toLowerCase(bytes, buffer, false);
    Assert.assertEquals(buffer.toString(), input != null ? lower : "");
  }

  /**
   * Tests the {@link StaticUtils#toUpperCase(String)} method.
   *
   * @param input
   *          The test string.
   * @param lower
   *          The test string in lower case.
   * @param upper
   *          The test string in upper case.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "stringCaseConversionTestData")
  public void testToUpperCaseString(String input, String lower, String upper)
      throws Exception {
    Assert.assertEquals(StaticUtils.toUpperCase(input), upper);
  }

  /**
   * Tests the
   * {@link StaticUtils#toUpperCase(byte[], StringBuilder, boolean)}
   * method.
   *
   * @param input
   *          The test string.
   * @param lower
   *          The test string in lower case.
   * @param upper
   *          The test string in upper case.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "stringCaseConversionTestData")
  public void testToUpperCaseBytes(String input, String lower, String upper)
      throws Exception {
    byte[] bytes = input != null ? input.getBytes("UTF-8") : null;
    StringBuilder buffer = new StringBuilder();
    StaticUtils.toUpperCase(bytes, buffer, false);
    Assert.assertEquals(buffer.toString(), input != null ? upper : "");
  }

  /**
   * Create test strings for the
   * {@link StaticUtils#toRFC3641StringValue(StringBuilder, String)}
   * method.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "toRFC3641StringValueTestData")
  public Object[][] createToRFC3641StringValueTestData() {
    return new Object[][] { { "", "\"\"" }, { " ", "\" \"" },
        { "  a  B  c  ", "\"  a  B  c  \"" },
        { "  \"hello world\"  ", "\"  \"\"hello world\"\"  \"" },
        { "\"\"\"", "\"\"\"\"\"\"\"\"" }, };
  }

  /**
   * Tests the
   * {@link StaticUtils#toRFC3641StringValue(StringBuilder, String)}
   * method.
   *
   * @param input
   *          The test string.
   * @param expected
   *          The expected result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "toRFC3641StringValueTestData")
  public void testToRFC3641StringValue(String input, String expected)
      throws Exception {
    StringBuilder builder = new StringBuilder();
    StaticUtils.toRFC3641StringValue(builder, input);
    Assert.assertEquals(builder.toString(), expected);
  }

  /**
   * Create test lists for the
   * {@link StaticUtils#listsAreEqual(List, List)} method.
   *
   * @return Returns an array of test data.
   */
  @DataProvider(name = "listsAreEqualTestData")
  public Object[][] createListsAreEqualTestData() {
    return new Object[][] {
        // Check null behaviour.
        { null, null, true },
        { null, Collections.emptyList(), false },
        { Collections.emptyList(), null, false },

        // Check empty-list behaviour.
        { Collections.emptyList(), Collections.emptyList(), true },
        { Collections.singletonList(0), Collections.emptyList(), false },
        { Collections.emptyList(), Collections.singletonList(0), false },

        // Check single-element behaviour.
        { Collections.singletonList(0), Collections.singletonList(0), true },
        { Collections.singletonList(0), Collections.singletonList(1), false },

        // Check multi-element random access behaviour.
        { Arrays.asList(0, 1), Arrays.asList(0, 1), true },
        { Arrays.asList(0, 1), Arrays.asList(1, 0), false },

        // ...With duplicates.
        { Arrays.asList(0, 1), Arrays.asList(0, 1, 1), false },

        // Check multi-element sequential behaviour.
        { new LinkedList<Integer>(Arrays.asList(0, 1)),
            new LinkedList<Integer>(Arrays.asList(0, 1)), true },
        { new LinkedList<Integer>(Arrays.asList(0, 1)),
            new LinkedList<Integer>(Arrays.asList(1, 0)), false },

        // ...With duplicates.
        { new LinkedList<Integer>(Arrays.asList(0, 1)),
            new LinkedList<Integer>(Arrays.asList(0, 1, 1)), false } };
  }

  /**
   * Tests the {@link StaticUtils#listsAreEqual(List, List)} method.
   *
   * @param list1
   *          The first list.
   * @param list2
   *          The second list.
   * @param result
   *          The expected equality result.
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dataProvider = "listsAreEqualTestData")
  public void testListsAreEqual(List list1, List list2, boolean result)
      throws Exception {
    Assert.assertEquals(StaticUtils.listsAreEqual(list1, list2), result);
  }
}
