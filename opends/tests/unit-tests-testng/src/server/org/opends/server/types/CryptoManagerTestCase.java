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
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import java.lang.reflect.Method;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import javax.crypto.Mac;

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


  @Test
  public void testMacSuccess()
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String text = "1234";

    final String macKeyID = cm.getMacEngineKeyEntryID();

    final Mac signingMac = cm.getMacEngine(macKeyID);
    final byte[] signedHash = signingMac.doFinal(text.getBytes());

    final Mac validatingMac = cm.getMacEngine(macKeyID);
    final byte[] calculatedSignature = validatingMac.doFinal(text.getBytes());
    
    assertTrue(Arrays.equals(calculatedSignature, signedHash));
  }


  /**
   Cipher parameters
   */
  private class CipherParameters {
    private final String fAlgorithm;
    private final String fMode;
    private final String fPadding;
    private final int fKeyLength;
    private final int fIVLength;

    public CipherParameters(final String algorithm, final String mode,
                            final String padding, final int keyLength,
                            final int ivLength) {
      fAlgorithm = algorithm;
      fMode = mode;
      fPadding = padding;
      fKeyLength = keyLength;
      fIVLength = ivLength;
    }

    public String getTransformation() {
      if (null == fAlgorithm) return null; // default
      return (null == fMode)
              ? new String(fAlgorithm)
              : (new StringBuilder(fAlgorithm)).append("/").append(fMode)
                .append("/").append(fPadding).toString();
    }

    public int getKeyLength() {
      return fKeyLength;
    }

    public int getIVLength() {
      return fIVLength;
    }
  }


  /**
   Cipher parameter data set.

   @return The set of Cipher parameters with which to test.
   */
  @DataProvider(name = "cipherParametersData")
  public Object[][] cipherParametersData() {

    List<CipherParameters> paramList = new LinkedList<CipherParameters>();
    // default (preferred) AES/CBC/PKCS5Padding 128bit key.
    paramList.add(new CipherParameters(null, null, null, 128, 128));
    // custom
//    paramList.add(new CipherParameters("Blowfish", "CFB", "NoPadding", 192, 64));
    paramList.add(new CipherParameters("Blowfish", "CFB", "NoPadding", 128, 64));
    paramList.add(new CipherParameters("RC4", null, null, 104, 0));
    paramList.add(new CipherParameters("DES", "CFB", "NoPadding", 56, 56));
    paramList.add(new CipherParameters("DESede", "ECB", "PKCS5Padding", 168, 56));


    Object[][] cipherParameters = new Object[paramList.size()][1];
    for (int i=0; i < paramList.size(); i++)
    {
      cipherParameters[i] = new Object[] { paramList.get(i) };
    }

    return cipherParameters;
  }


  /**
   Tests a simple encryption-decryption cycle using the supplied cipher
   parameters.

   @param cp  Cipher parameters to use for this test iteration.

   @throws Exception If an exceptional condition arises.
   */
  @Test(dataProvider = "cipherParametersData")
  public void testEncryptDecryptSuccess(CipherParameters cp)
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = (null == cp.getTransformation())
            ? cm.encrypt(secretMessage.getBytes()) // default
            : cm.encrypt(cp.getTransformation(), cp.getKeyLength(),
                         secretMessage.getBytes());
    assertEquals(-1, (new String(cipherText)).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
  }


  /**
   Tests a simple cipher stream encryption-decryption cycle using the supplied
   cipher parameters.

   @param cp  Cipher parameters to use for this test iteration.

   @throws Exception If an exceptional condition arises.
   */
  @Test(dataProvider = "cipherParametersData")
  public void testStreamEncryptDecryptSuccess(CipherParameters cp)
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = (null == cp.getTransformation())
            ? cm.getCipherOutputStream(os) // default
            : cm.getCipherOutputStream(cp.getTransformation(), cp.getKeyLength(), os);
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
   Tests to ensure the same key identifier (and hence, key) is used for
   successive encryptions specifying the same algorithm and key length.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test
  public void testKeyEntryReuse()
          throws Exception {

    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "1234";

    try {
      Method m = Arrays.class.getMethod("copyOfRange", (new byte[16]).getClass(),
              Integer.TYPE, Integer.TYPE);
      final byte[] cipherText = cm.encrypt(secretMessage.getBytes());
      final byte[] keyID = (byte[])m.invoke(null, cipherText, 0, 16);
      final byte[] cipherText2 = cm.encrypt(secretMessage.getBytes());
      final byte[] keyID2 = (byte[])m.invoke(null, cipherText2, 0, 16);
      assertTrue(Arrays.equals(keyID, keyID2));
    }
    catch (NoSuchMethodException ex) {
      // ignore - requires Java 6
    }
  }
}
