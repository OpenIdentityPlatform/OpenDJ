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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.util;



import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Arrays;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.util.Platform.KeyType;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the certificate manager class.
 */
public class CertificateManagerTestCase
       extends UtilTestCase
{
  /**
   * Indicates whether the certificate manager is available on this platform and
   * should be tested.
   */
  public static final boolean CERT_MANAGER_AVAILABLE =
       CertificateManager.mayUseCertificateManager();


  /** Get the build root and use it to create a test package directory. */
  public static final String BUILD_ROOT =
          System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);


  /**
   * The path to a JKS key store file.
   */
  public static final String JKS_KEY_STORE_PATH = TestCaseUtils.getUnitTestRootPath()
       + File.separator + "package-instance" + File.separator + "config" + File.separator + "server.keystore";



  /**
   * The path to a PKCS#12 key store file.
   */
  public static final String PKCS12_KEY_STORE_PATH = TestCaseUtils.getUnitTestRootPath()
       + File.separator + "package-instance" + File.separator + "config" + File.separator + "server-cert.p12";



  /**
   * The path to the unit test working directory.
   */
  public static final String TEST_DIR = TestCaseUtils.getUnitTestRootPath()
       + File.separator + "package-instance";



  /**
   * Make sure the server is running.
   *
   * @throws  Exception  If a problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }

  @DataProvider(name="keyTypes")
  public Object[][] keyTypes() {
    return new Object[][] {
      { KeyType.EC },
      { KeyType.RSA }
    };
  }

  /**
   * Tests the CertificateManager constructor using a null argument for the key
   * store path.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { NullPointerException.class })
  public void testConstructorNullPath()
         throws Exception
  {
    new CertificateManager(null, "JKS", "password");
  }



  /**
   * Tests the CertificateManager constructor using an empty string for the key
   * store path.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { NullPointerException.class })
  public void testConstructorEmptyPath()
         throws Exception
  {
    new CertificateManager("", "JKS", "password");
  }



  /**
   * Tests the CertificateManager constructor using a key store path that refers
   * to a file in a nonexistent directory.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { IllegalArgumentException.class })
  public void testConstructorNonexistentPath()
         throws Exception
  {
    String path = TEST_DIR + File.separator + "nonexistent" + File.separator +
                  "doesntmatter";

    new CertificateManager(path, "JKS", "password");
  }



  /**
   * Tests the CertificateManager constructor using a key store path that refers
   * to a file that exists but isn't a file.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { IllegalArgumentException.class })
  public void testConstructorPathNotFile()
         throws Exception
  {
    new CertificateManager(TEST_DIR, "JKS", "password");
  }



  /**
   * Tests the CertificateManager constructor using a null argument for the key
   * store type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { NullPointerException.class })
  public void testConstructorNullType()
         throws Exception
  {
    new CertificateManager(JKS_KEY_STORE_PATH, null, "password");
  }



  /**
   * Tests the CertificateManager constructor using an empty string for the key
   * store type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { NullPointerException.class })
  public void testConstructorEmptyType()
         throws Exception
  {
    new CertificateManager(JKS_KEY_STORE_PATH, "", "password");
  }



  /**
   * Tests the CertificateManager constructor using an invalid key store type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { IllegalArgumentException.class })
  public void testConstructorInvalidType()
         throws Exception
  {
    new CertificateManager(JKS_KEY_STORE_PATH, "invalid", "password");
  }



  /**
   * Tests the CertificateManager constructor using an invalid key store path
   * in conjunction with the PKCS11 key store type..
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { IllegalArgumentException.class })
  public void testConstructorInvalidPKCS11Path()
         throws Exception
  {
    new CertificateManager(JKS_KEY_STORE_PATH, "PKCS11", "password");
  }



  /**
   * Tests the CertificateManager constructor using a null argument for the key
   * store PIN.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testConstructorNullPIN()
         throws Exception
  {
    assertNotNull(new CertificateManager(JKS_KEY_STORE_PATH, "JKS", (String) null));
  }



  /**
   * Tests the CertificateManager constructor using an empty string for the key
   * store PIN.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testConstructorEmptyPIN()
         throws Exception
  {
    assertNotNull(new CertificateManager(JKS_KEY_STORE_PATH, "JKS", ""));
  }



  /**
   * Tests the certificate manager with a valid constructor using the JKS key
   * store type.
   */
  @Test
  public void testValidConstructorJKS()
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");
  }



  /**
   * Tests the certificate manager with a valid constructor using the PKCS12 key
   * store type.
   */
  @Test
  public void testValidConstructorPKCS12()
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    new CertificateManager(PKCS12_KEY_STORE_PATH, "PKCS12", "password");
  }



  /**
   * Tests the {@code aliasInUse} method with a null alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAliasInUseNull()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.aliasInUse(null);
      fail("Expected an NPE due to null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code aliasInUse} method with an empty alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAliasInUseEmpty()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.aliasInUse("");
      fail("Expected an NPE due to empty alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code aliasInUse} method with an invalid key store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAliasInUseInvalidKeyStore()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testAliasInUseInvalidKeyStore",
                                    ".notakeystore");
    path.deleteOnExit();
    FileOutputStream outputStream = new FileOutputStream(path, false);
    outputStream.write("This is not a valid key store.".getBytes());
    outputStream.close();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "JKS", "password");

    try
    {
      certManager.aliasInUse("doesntmatter");
      fail("Expected a key store exception due to an invalid key store");
    } catch (KeyStoreException kse) {}

    path.delete();
  }



  /**
   * Tests the {@code aliasInUse} method for a key store using the JKS type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAliasInUseJKS()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");
    assertTrue(certManager.aliasInUse("server-cert"));
    assertFalse(certManager.aliasInUse("nonexistent"));

    String path = TEST_DIR + File.separator + "nonexistent";
    certManager = new CertificateManager(path, "JKS", "password");
    assertFalse(certManager.aliasInUse("doesntmatter"));
  }



  /**
   * Tests the {@code aliasInUse} method for a key store using the PKCS12 type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAliasInUsePKCS12()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(PKCS12_KEY_STORE_PATH, "PKCS12", "password");
    assertTrue(certManager.aliasInUse("server-cert"));
    assertFalse(certManager.aliasInUse("nonexistent"));

    String path = TEST_DIR + File.separator + "nonexistent";
    certManager = new CertificateManager(path, "PKCS12", "password");
    assertFalse(certManager.aliasInUse("doesntmatter"));
  }



  /**
   * Tests the {@code getCertificateAliases} method for a key store using the
   * JKS type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificateAliasesJKS()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    String[] aliases = certManager.getCertificateAliases();
    assertNotNull(aliases);
    assertTrue(aliases.length > 0);
    assertTrue(Arrays.asList(aliases).contains("server-cert"));

    String path = TEST_DIR + File.separator + "nonexistent";
    certManager = new CertificateManager(path, "JKS", "password");
    assertNull(certManager.getCertificateAliases());
  }



  /**
   * Tests the {@code getCertificateAliases} method for a key store using the
   * PKCS12 type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificateAliasesPKCS12()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(PKCS12_KEY_STORE_PATH, "PKCS12", "password");

    String[] aliases = certManager.getCertificateAliases();
    assertNotNull(aliases);
    assertTrue(aliases.length > 0);
    assertTrue(Arrays.asList(aliases).contains("server-cert"));

    String path = TEST_DIR + File.separator + "nonexistent";
    certManager = new CertificateManager(path, "PKCS12", "password");
    assertNull(certManager.getCertificateAliases());
  }



  /**
   * Tests the {@code getCertificate} method using a null alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificateNull()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.getCertificate(null);
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code getCertificate} method using an empty alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificateEmpty()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.getCertificate("");
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code getCertificate} method for a key store using the JKS type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificateJKS()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");
    assertNotNull(certManager.getCertificate("server-cert"));
    assertNull(certManager.getCertificate("nonexistent"));

    String path = TEST_DIR + File.separator + "nonexistent";
    certManager = new CertificateManager(path, "JKS", "password");
    try
    {
      certManager.getCertificate("doesntmatter");
      fail("Expected a key store exception due to a nonexistent key store");
    } catch (KeyStoreException kse) {}
  }



  /**
   * Tests the {@code getCertificate} method for a key store using the PKCS12
   * type.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificatePKCS12()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(PKCS12_KEY_STORE_PATH, "PKCS12", "password");
    assertNotNull(certManager.getCertificate("server-cert"));
    assertNull(certManager.getCertificate("nonexistent"));

    String path = TEST_DIR + File.separator + "nonexistent";
    certManager = new CertificateManager(path, "PKCS12", "password");
    try
    {
      certManager.getCertificate("doesntmatter");
      fail("Expected a key store exception due to a nonexistent key store");
    } catch (KeyStoreException kse) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using a null alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateNullAlias(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.generateSelfSignedCertificate(keyType, null, "CN=Test,O=test", 365);
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an empty
   * alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateEmptyAlias(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.generateSelfSignedCertificate(keyType, "", "CN=Test,O=test", 365);
      fail("Expected an NPE due to an empty alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an alias
   * that's already being used.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateAliasInUse(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.generateSelfSignedCertificate(keyType, "server-cert", "CN=Test,O=test",
                                                365);
      fail("Expected an illegal argument exception to a duplicate alias");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using a null
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateNullSubject(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.generateSelfSignedCertificate(keyType, "test-cert", null, 365);
      fail("Expected an NPE due to a null subject");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an empty
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateEmptySubject(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.generateSelfSignedCertificate(keyType, "test-cert", "", 365);
      fail("Expected an NPE due to an empty subject");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an invalid
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateInvalidSubject(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testGenerateSelfSignedCertificateJKS",
                                    ".keystore");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "JKS", "password");
    try
    {
      certManager.generateSelfSignedCertificate(keyType, "test-cert", "invalid", 365);
      fail("Expected a key store exception due to an invalid subject");
    } catch (KeyStoreException cse) {}
    path.delete();
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an invalid
   * validity.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateInvalidValidity(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.generateSelfSignedCertificate(keyType, "test-cert", "CN=Test,o=test",
                                                0);
      fail("Expected an illegal argument exception due to an invalid validity");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method for a JKS key store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificateJKS(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testGenerateSelfSignedCertificateJKS",
                                    ".keystore");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "JKS", "password");
    certManager.generateSelfSignedCertificate(keyType, "test-cert", "CN=Test,o=test",
                                              365);
    assertTrue(certManager.aliasInUse("test-cert"));
    path.delete();
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method for a PKCS12 key
   * store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(groups="slow", dataProvider="keyTypes")
  public void testGenerateSelfSignedCertificatePKCS12(KeyType keyType)
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testGenerateSelfSignedCertificatePKCS12",
                                    ".p12");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "PKCS12", "password");
    certManager.generateSelfSignedCertificate(keyType, "test-cert", "CN=Test,o=test",
                                              365);
    assertTrue(certManager.aliasInUse("test-cert"));
    path.delete();
  }



  /**
   * Tests the {@code addCertificate} method using a null alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddCertificateNullAlias()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");
    File exportFile = exportCertificate();

    try
    {
      certManager.addCertificate(null, exportFile);
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}

    exportFile.delete();
  }



  /**
   * Tests the {@code addCertificate} method using an empty alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddCertificateEmptyAlias()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");
    File exportFile = exportCertificate();

    try
    {
      certManager.addCertificate("", exportFile);
      fail("Expected an NPE due to an empty alias");
    } catch (NullPointerException npe) {}

    exportFile.delete();
  }



  /**
   * Tests the {@code addCertificate} method using a null certificate file.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddCertificateNullCertificateFile()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.addCertificate("test-cert", null);
      fail("Expected an NPE due to a null certificate file");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code addCertificate} method using a certificate file that does
   * not exist.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddCertificateMissingCertificateFile()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    File f = new File(TEST_DIR, "nonexistent");

    try
    {
      certManager.addCertificate("test-cert", f);
      fail("Expected an illegal argument exception due to a missing " +
           "certificate file");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code addCertificate} method using a certificate file that is
   * not a file.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddCertificateFileNotFile()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    File f = new File(TEST_DIR);

    try
    {
      certManager.addCertificate("test-cert", f);
      fail("Expected an illegal argument exception due to a certificate file " +
           "actually being a directory");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code addCertificate} method using a certificate file that
   * contains something other than a certificate.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddCertificateFileNotCertificate()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    File path = File.createTempFile("testAddCertificateFileNotCertificate",
                                    ".notacertificate");
    path.deleteOnExit();
    FileOutputStream outputStream = new FileOutputStream(path, false);
    outputStream.write("This is not a valid certificate.".getBytes());
    outputStream.close();

    try
    {
      certManager.addCertificate("test-cert", path);
      fail("Expected a key store exception due to an invalid certificate");
    } catch (KeyStoreException kse) {}

    path.delete();
  }



  /**
   * Tests the {@code removeCertificate} method using a null alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testRemoveCertificateNullAlias()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.removeCertificate(null);
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code removeCertificate} method using an empty alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testRemoveCertificateEmptyAlias()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.removeCertificate("");
      fail("Expected an NPE due to an empty alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code removeCertificate} method using an alias that doesn't
   * exist.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testRemoveCertificateNonexistentAlias()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    try
    {
      certManager.removeCertificate("nonexistent");
      fail("Expected an illegal argument exception due to a nonexistent alias");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code addCertificate} and {@code removeCertificate} methods
   * using a newly-created JKS key store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddAndRemoveCertificateJKS()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testAddAndRemoveCertificateJKS",
                                    ".keystore");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "JKS", "password");

    File exportFile = exportCertificate();
    certManager.addCertificate("test-cert", exportFile);
    assertTrue(certManager.aliasInUse("test-cert"));
    assertNotNull(certManager.getCertificate("test-cert"));

    certManager.removeCertificate("test-cert");
    assertFalse(certManager.aliasInUse("test-cert"));
    assertNull(certManager.getCertificate("test-cert"));

    exportFile.delete();
    path.delete();
  }



  /**
   * Exports a certificate to a temporary file.
   *
   * @throws  Exception  If a problem occurs.
   */
  private File exportCertificate()
          throws Exception
  {
    File path = File.createTempFile("exportCertificate",
                                    ".cert");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "password");

    Certificate certificate = certManager.getCertificate("server-cert");
    assertNotNull(certificate);

    byte[] certificateBytes = certificate.getEncoded();
    assertNotNull(certificateBytes);
    assertTrue(certificateBytes.length > 0);

    FileOutputStream outputStream = new FileOutputStream(path, false);
    outputStream.write(certificateBytes);
    outputStream.close();

    return path;
  }
}

