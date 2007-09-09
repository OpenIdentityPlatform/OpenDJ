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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.opends.server.TestCaseUtils;

import org.opends.server.core.DirectoryServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 This class tests the CryptoManager.
 */
public class CryptoManagerTestCase extends TypesTestCase
{
  /**
   Setup..
   @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void setUp()
         throws Exception {
    TestCaseUtils.startServer();
  }

  /**
   Cleanup.
   @throws Exception If an exceptional condition arises.
   */
  @AfterClass()
  public void CleanUp() throws Exception {

  }

  /**
   Tests a simple encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testEncryptDecryptSuccess() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = cm.encrypt(secretMessage.getBytes());
    assertEquals(-1, (new String(cipherText)).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
  }

  /**
   Tests a simple cipher stream encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testCipherEncryptDecryptSuccess() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = cm.getCipherOutputStream(os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }

  // TODO: other-than-preferred cipher algorithms, failure cases...
  /**
   Tests a simple encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testEncryptDecryptSuccessX() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = cm.encrypt("Blowfish/CFB/NoPadding", 128,
            secretMessage.getBytes());
    assertEquals(-1, (new String(cipherText)).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
  }

  /**
   Tests a simple cipher stream encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testCipherEncryptDecryptSuccessX() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = cm.getCipherOutputStream("Blowfish/CFB/NoPadding", 128, os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }

  /**
   Tests a simple encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testEncryptDecryptSuccessY() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = cm.encrypt("RC4", 104,
            secretMessage.getBytes());
    assertEquals(-1, (new String(cipherText)).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
  }

  /**
   Tests a simple cipher stream encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testCipherEncryptDecryptSuccessY() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = cm.getCipherOutputStream("RC4", 104, os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }

  /**
   Tests a simple encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testEncryptDecryptSuccessZ() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = cm.encrypt("DES/CFB/NoPadding", 56,
            secretMessage.getBytes());
    assertEquals(-1, (new String(cipherText)).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
  }

  /**
   Tests a simple cipher stream encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testCipherEncryptDecryptSuccessZ() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = cm.getCipherOutputStream("DES/CFB/NoPadding", 56, os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }

  /**
   Tests a simple encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testEncryptDecryptSuccessZZ() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = cm.encrypt("DESede/ECB/PKCS5Padding", 168,
            secretMessage.getBytes());
    assertEquals(-1, (new String(cipherText)).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
  }

  /**
   Tests a simple cipher stream encryption-decryption cycle.

   @throws Exception If an exceptional condition arises.
   */
  @Test
  public void testCipherEncryptDecryptSuccessZZ() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = cm.getCipherOutputStream("DESede/ECB/PKCS5Padding", 168, os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }
}
