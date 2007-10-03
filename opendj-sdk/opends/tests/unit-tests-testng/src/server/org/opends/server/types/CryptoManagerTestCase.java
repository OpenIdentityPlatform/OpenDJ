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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.opends.server.TestCaseUtils;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.schema.BinarySyntax;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.config.ConfigConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

import org.opends.server.core.DirectoryServer;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.ADSContext;
import org.opends.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.lang.reflect.Method;
import java.security.MessageDigest;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import javax.crypto.Mac;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.InitialLdapContext;

/**
 This class tests the CryptoManager.
 */
public class CryptoManagerTestCase extends TypesTestCase
{
  /**
   Setup..
   @throws Exception  If an unexpected problem occurs.
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
    DirectoryServer.restart(this.getClass().getName(),
            Message.raw("CryptoManager: clean-up internal key caches."));
  }


  @Test
  public void testGetInstanceKeyCertificate()
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final byte[] cert = cm.getInstanceKeyCertificate();
    assertNotNull(cert);

    // The certificate should now be accessible in the truststore backend via LDAP.
    final InitialLdapContext ctx = ConnectionUtils.createLdapContext(
            "ldap://" + "127.0.0.1" + ":"
                    + String.valueOf(TestCaseUtils.getServerLdapPort()),
            "cn=Directory Manager", "password",
          ConnectionUtils.getDefaultLDAPTimeout(), null);
    // TODO: the below dn should be in ConfigConstants
    final String dnStr = "ds-cfg-key-id=ads-certificate,cn=ads-truststore";
    final LdapName dn = new LdapName(dnStr);
    final SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    final String attrIDs[] = { "ds-cfg-public-key-certificate;binary" };
    searchControls.setReturningAttributes(attrIDs);
    final SearchResult certEntry = ctx.search(dn,
               "(objectclass=ds-cfg-instance-key)", searchControls).next();
    final javax.naming.directory.Attribute certAttr
            = certEntry.getAttributes().get(attrIDs[0]);
    /* attribute ds-cfg-public-key-certificate is a MUST in the schema */
    assertNotNull(certAttr);
    byte[] ldapCert = (byte[])certAttr.get();
    // Compare the certificate values.
    assertTrue(Arrays.equals(ldapCert, cert));

    // Compare the MD5 hash of the LDAP attribute with the one
    // retrieved from the CryptoManager.
    MessageDigest md = MessageDigest.getInstance("MD5");
    assertTrue(StaticUtils.bytesToHexNoSpace(
         md.digest(ldapCert)).equals(cm.getInstanceKeyID()));
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

  // TODO: other-than-default MAC

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
// TODO:  paramList.add(new CipherParameters("Blowfish", "CFB", "NoPadding", 192, 64));
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
@Test(dataProvider="cipherParametersData")
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
  @Test(dataProvider="cipherParametersData")
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
   <p>
   The default encryption cipher requires an initialization vector. Confirm
   successive uses of a key produces distinct ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test
  public void testKeyEntryReuse()
          throws Exception {

    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt(secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt(secretMessage.getBytes());

    // test cycle
    final byte[] plainText = cm.decrypt(cipherText2);
    assertEquals((new String(plainText)), secretMessage);

    // test for identical keys
    try {
      Method m = Arrays.class.getMethod("copyOfRange", (new byte[16]).getClass(),
              Integer.TYPE, Integer.TYPE);
      final byte[] keyID = (byte[])m.invoke(null, cipherText, 0, 16);
      final byte[] keyID2 = (byte[])m.invoke(null, cipherText2, 0, 16);
      assertEquals(keyID, keyID2);
    }
    catch (NoSuchMethodException ex) {
      // skip this test - requires at least Java 6
    }

    // test for distinct ciphertext
    assertTrue(! Arrays.equals(cipherText, cipherText2));
  }


  /**
   Test that secret keys are persisted: Encrypt some data using a
   variety of transformations, restart the instance, and decrypt the
   retained ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test(enabled=false)
  public void testKeyPersistence()
        throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt("Blowfish/CFB/NoPadding", 128,
            secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt("RC4", 104,
            secretMessage.getBytes());

    DirectoryServer.restart(this.getClass().getName(),
            Message.raw("CryptoManager: testing persistent secret keys."));

    byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);
    plainText = cm.decrypt(cipherText2);
    assertEquals((new String(plainText)), secretMessage);
  }


  /**
   Mark a key compromised; ensure 1) subsequent encryption requests use a new
   key; 2) ciphertext produced using the compromised key can still be decrypted.

   @throws Exception In case something exceptional happens.
   */
  @Test(enabled=false)
  public void testCompromisedKey() throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";
    final String cipherTransformationName = "AES/CBC/PKCS5Padding";
    final int cipherKeyLength = 128;

    // Initial encryption ensures a cipher key entry is in ADS.
    final byte[] cipherText = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // Retrieve all uncompromised cipher key entries corresponding to the
    // specified transformation and key length. Mark each entry compromised.
    final String baseDNStr // TODO: is this DN a constant?
            = "cn=secret keys," + ADSContext.getAdministrationSuffixDN();
    final DN baseDN = DN.decode(baseDNStr);
    final String FILTER_OC_INSTANCE_KEY
            = new StringBuilder("(objectclass=")
            .append(ConfigConstants.OC_CRYPTO_CIPHER_KEY)
            .append(")").toString();
    final String FILTER_NOT_COMPROMISED = new StringBuilder("(!(")
            .append(ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME)
            .append("=*))").toString();
    final String FILTER_CIPHER_TRANSFORMATION_NAME = new StringBuilder("(")
            .append(ConfigConstants.ATTR_CRYPTO_CIPHER_TRANSFORMATION_NAME)
            .append("=").append(cipherTransformationName)
            .append(")").toString();
    final String FILTER_CIPHER_KEY_LENGTH = new StringBuilder("(")
            .append(ConfigConstants.ATTR_CRYPTO_KEY_LENGTH_BITS)
            .append("=").append(String.valueOf(cipherKeyLength))
            .append(")").toString();
    final String searchFilter = new StringBuilder("(&")
            .append(FILTER_OC_INSTANCE_KEY)
            .append(FILTER_NOT_COMPROMISED)
            .append(FILTER_CIPHER_TRANSFORMATION_NAME)
            .append(FILTER_CIPHER_KEY_LENGTH)
            .append(")").toString();
    final LinkedHashSet<String> requestedAttributes
            = new LinkedHashSet<String>();
    requestedAttributes.add("dn");
    final InternalClientConnection icc
            = InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOp = icc.processSearch(
            baseDN,
            SearchScope.SINGLE_LEVEL,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            /* size limit */ 0, /* time limit */ 0,
            /* types only */ false,
            SearchFilter.createFilterFromString(searchFilter),
            requestedAttributes);

    assertTrue(0 < searchOp.getSearchEntries().size());
    String compromisedTime = TimeThread.getGeneralizedTime();
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(
        "dn: " + e.getDN().toNormalizedString(),
        "changetype: modify",
        "replace: " + ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME,
        ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME + ": "
                + compromisedTime);
    }

    // Use the transformation and key length again. A new cipher key
    // should be produced.
    final byte[] cipherText2 = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // test for identical keys
    try {
      Method m = Arrays.class.getMethod("copyOfRange", (new byte[16]).getClass(),
              Integer.TYPE, Integer.TYPE);
      final byte[] keyID = (byte[])m.invoke(null, cipherText, 0, 16);
      final byte[] keyID2 = (byte[])m.invoke(null, cipherText2, 0, 16);
      assertTrue(! Arrays.equals(keyID, keyID2));
    }
    catch (NoSuchMethodException ex) {
      // skip this test - requires at least Java 6
    }

    // confirm ciphertext produced using compromised key can still
    // be decrypted.
    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals((new String(plainText)), secretMessage);

  }

}
