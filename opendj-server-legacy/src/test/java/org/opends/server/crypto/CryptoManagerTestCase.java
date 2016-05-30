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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.admin.ads.ADSContext;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.CryptoManagerException;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.types.Attributes.create;
import static org.testng.Assert.*;

/**
 This class tests the CryptoManager.
 */
@SuppressWarnings("javadoc")
public class CryptoManagerTestCase extends CryptoTestCase {

  @BeforeClass
  public void setUp()
         throws Exception {
    TestCaseUtils.startServer();
  }

  @AfterClass
  public void CleanUp() throws Exception {
    // Removes at least secret keys added in this test case.
    TestCaseUtils.restartServer();
  }

  @Test
  public void testImportKeysUsesLatestKey()
      throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final int keyLength = 56;
    final String cipher = "DES/CFB/NoPadding";
    byte[] cipherText = cm.encrypt(cipher, keyLength, new byte[56]);
    Entry oldKey = getKeyForCipher(cipher, keyLength);
    // Force import by changing the keyID
    Modification mod = new Modification(REPLACE, create("ds-cfg-key-id", UUID.randomUUID().toString()));
    oldKey.applyModification(mod);
    cm.importCipherKeyEntry(oldKey);
    byte[] newCipherText = cm.encrypt(cipher, keyLength, new byte[56]);
    assertThat(ByteString.wrap(cipherText, 1, 16).compareTo(newCipherText, 1, 16)).isNotEqualTo(0);
  }

  private Entry getKeyForCipher(String cipher, int keyLength) throws DirectoryException
  {
    SearchRequest request = newSearchRequest("cn=secret keys, cn=admin data", SearchScope.WHOLE_SUBTREE,
        "&(ds-cfg-cipher-transformation-name=" + cipher + ")(ds-cfg-key-length-bits=" + keyLength + ")");
    InternalClientConnection conn = getRootConnection();
    InternalSearchOperation search = conn.processSearch(request);
    return search.getSearchEntries().get(0);
  }

  @Test
  public void testGetInstanceKeyCertificate()
          throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final byte[] cert
            = CryptoManagerImpl.getInstanceKeyCertificateFromLocalTruststore();
    assertNotNull(cert);

    // The certificate should now be accessible in the truststore backend via LDAP.
    ByteString ldapCert;
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerAdminPort(), true))
    {
      conn.bind("cn=Directory Manager", "password");

      // TODO: should the below dn be in ConfigConstants?
      final String dnStr = "ds-cfg-key-id=ads-certificate,cn=ads-truststore";
      conn.search(dnStr, SearchScope.BASE_OBJECT, "(objectclass=ds-cfg-instance-key)",
          "ds-cfg-public-key-certificate;binary");
      List<SearchResultEntryProtocolOp> searchEntries = conn.readEntries();
      assertThat(searchEntries).hasSize(1);
      SearchResultEntryProtocolOp searchEntry = searchEntries.get(0);
      List<LDAPAttribute> attributes = searchEntry.getAttributes();
      assertThat(attributes).hasSize(1);
      LDAPAttribute certAttr = attributes.get(0);
      /* attribute ds-cfg-public-key-certificate is a MUST in the schema */
      assertNotNull(certAttr);
      List<ByteString> values = certAttr.getValues();
      assertThat(values).hasSize(1);
      ldapCert = values.get(0);
      // Compare the certificate values.
      assertEquals(ldapCert.toByteArray(), cert);
    }

    // Compare the MD5 hash of the LDAP attribute with the one
    // retrieved from the CryptoManager.
    MessageDigest md = MessageDigest.getInstance("MD5");
    String actual = StaticUtils.bytesToHexNoSpace(md.digest(ldapCert.toByteArray()));
    assertEquals(actual, cm.getInstanceKeyID());

    // Call twice to ensure idempotent.
    CryptoManagerImpl.publishInstanceKeyEntryInADS();
    CryptoManagerImpl.publishInstanceKeyEntryInADS();
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

  private class CipherParameters {
    private final String fAlgorithm;
    private final String fMode;
    private final String fPadding;
    private final int fKeyLength;

    public CipherParameters(final String algorithm, final String mode,
                            final String padding, final int keyLength) {
      fAlgorithm = algorithm;
      fMode = mode;
      fPadding = padding;
      fKeyLength = keyLength;
    }

    public String getTransformation() {
      if (null != fAlgorithm)
      {
        return fMode != null
                  ? fAlgorithm + "/" + fMode + "/" + fPadding
                  : fAlgorithm;
      }
      return null;
    }

    public int getKeyLength() {
      return fKeyLength;
    }
  }


  /**
   Cipher parameter data set.

   @return The set of Cipher parameters with which to test.
   */
  @DataProvider(name = "cipherParametersData")
  public Object[][] cipherParametersData() {
    return new Object[][] {
      // default (preferred) AES/CBC/PKCS5Padding 128bit key.
      { new CipherParameters(null, null, null, 128) },
      // custom
// TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2448
// TODO: { new CipherParameters("Blowfish", "CFB", "NoPadding", 448) },
// TODO: { new CipherParameters("AES", "CBC", "PKCS5Padding", 256) },
      { new CipherParameters("AES", "CFB", "NoPadding", 128) },
      { new CipherParameters("Blowfish", "CFB", "NoPadding", 128) },
      { new CipherParameters("RC4", null, null, 104) },
      { new CipherParameters("RC4", "NONE", "NoPadding", 128) },
      { new CipherParameters("DES", "CFB", "NoPadding", 56) },
      { new CipherParameters("DESede", "ECB", "PKCS5Padding", 168) },
    };
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
    assertEquals(-1, new String(cipherText).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);
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
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
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
    assertEquals(new String(plainText), secretMessage);

    // test for identical keys
    final byte[] keyID = Arrays.copyOfRange(cipherText, 1, 16);
    final byte[] keyID2 = Arrays.copyOfRange(cipherText2, 1, 16);
    assertTrue(Arrays.equals(keyID, keyID2));

    // test for distinct ciphertext
    assertFalse(Arrays.equals(cipherText, cipherText2));
  }


  /**
   Test that secret keys are persisted: Encrypt some data using a
   variety of transformations, restart the instance, and decrypt the
   retained ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test
  public void testKeyPersistence()
        throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt("Blowfish/CFB/NoPadding", 128,
            secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt("RC4", 104,
            secretMessage.getBytes());

    EmbeddedUtils.restartServer(
            this.getClass().getName(),
            LocalizableMessage.raw("CryptoManager: testing persistent secret keys."),
            DirectoryServer.getEnvironmentConfig());

    byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);
    plainText = cm.decrypt(cipherText2);
    assertEquals(new String(plainText), secretMessage);
  }


  /**
   Mark a key compromised; ensure 1) subsequent encryption requests use a
   new key; 2) ciphertext produced using the compromised key can still be
   decrypted; 3) once the compromised key entry is removed, confirm ciphertext
   produced using the compromised key can no longer be decrypted.

   @throws Exception In case something exceptional happens.
   */
  @Test
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
    final String baseDNStr // TODO: is this DN defined elsewhere as a constant?
            = "cn=secret keys," + ADSContext.getAdministrationSuffixDN();
    final DN baseDN = DN.valueOf(baseDNStr);
    final String FILTER_OC_INSTANCE_KEY = "(objectclass=" + OC_CRYPTO_CIPHER_KEY + ")";
    final String FILTER_NOT_COMPROMISED =
        "(!(" + ATTR_CRYPTO_KEY_COMPROMISED_TIME + "=*))";
    final String FILTER_CIPHER_TRANSFORMATION_NAME =
        "(" + ATTR_CRYPTO_CIPHER_TRANSFORMATION_NAME + "=" + cipherTransformationName + ")";
    final String FILTER_CIPHER_KEY_LENGTH =
        "(" + ATTR_CRYPTO_KEY_LENGTH_BITS + "=" + cipherKeyLength + ")";
    final String searchFilter =
        "(&"
        + FILTER_OC_INSTANCE_KEY
        + FILTER_NOT_COMPROMISED
        + FILTER_CIPHER_TRANSFORMATION_NAME
        + FILTER_CIPHER_KEY_LENGTH
        + ")";
    final SearchRequest request = newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, searchFilter).addAttribute("dn");
    InternalSearchOperation searchOp = getRootConnection().processSearch(request);
    assertFalse(searchOp.getSearchEntries().isEmpty());

    String compromisedTime = TimeThread.getGeneralizedTime();
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(true,
        "dn: " + e.getName(),
        "changetype: modify",
        "replace: " + ATTR_CRYPTO_KEY_COMPROMISED_TIME,
        ATTR_CRYPTO_KEY_COMPROMISED_TIME + ": " + compromisedTime);
    }
    //Wait so the above asynchronous modification can be applied. The crypto
    //manager's cipherKeyEntryCache needs to be updated before the encrypt()
    //method is called below.
    Thread.sleep(1000);
    // Use the transformation and key length again. A new cipher key
    // should be produced.
    final byte[] cipherText2 = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // 1. Test for distinct keys.
    final byte[] keyID = new byte[16];
    final byte[] keyID2 = new byte[16];
    System.arraycopy(cipherText, 1, keyID, 0, 16);
    System.arraycopy(cipherText2, 1, keyID2, 0, 16);
    assertFalse(Arrays.equals(keyID, keyID2));

    // 2. Confirm ciphertext produced using the compromised key can still be decrypted.
    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);

    // 3. Delete the compromised entry(ies) and ensure ciphertext produced
    // using a compromised key can no longer be decrypted.
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(true, "dn: " + e.getName(), "changetype: delete");
    }
    Thread.sleep(1000); // Clearing the cache is asynchronous.
    try {
      cm.decrypt(cipherText);
    }
    catch (CryptoManagerException ex) {
      // TODO: if reasons are added to CryptoManagerException, check for
      // expected cause.
    }
  }

  /**
   TODO: Test shared secret key wrapping (various wrapping ciphers, if configurable).
   */


  /**
   TODO: Test the secret key synchronization protocol.

     1. Create the first instance; add reversible password storage scheme
     to password policy; add entry using explicit password policy; confirm
     secret key entry has been produced.

     2. Create and initialize the second instance into the existing ADS domain.
     The secret key entries should be propagated to the second instance via
     replication. Then the new instance should detect that the secret key
     entries are missing ds-cfg-symmetric-key attribute values for that
     instance, inducing the key synchronization protocol.

     3. Confirm the second instance can decrypt the password of the entry
     added in step 1; e.g., bind as that user.

     4. Stop the second instance. At the first instance, enable a different
     reversible password storage scheme (different cipher transformation,
     and hence secret key entry); add another entry using that password
     storage scheme; start the second instance; ensure the password can
     be decrypted at the second instance.
     */
}
