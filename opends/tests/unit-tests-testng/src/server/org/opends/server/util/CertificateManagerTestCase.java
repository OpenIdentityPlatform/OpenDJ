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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Arrays;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;

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



  /**
   * The path to a JKS key store file.
   */
  public static final String JKS_KEY_STORE_PATH =
       System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT) + File.separator +
       "build" + File.separator + "unit-tests" + File.separator +
       "package-instance" +
       File.separator + "config" + File.separator + "server.keystore";



  /**
   * The path to a PKCS#12 key store file.
   */
  public static final String PKCS12_KEY_STORE_PATH =
       System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT) + File.separator +
       "build" + File.separator + "unit-tests" + File.separator +
       "package-instance" +
       File.separator + "config" + File.separator + "server-cert.p12";



  /**
   * The path to the unit test working directory.
   */
  public static final String TEST_DIR =
       System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT) + File.separator +
       "build" + File.separator + "unit-tests" + File.separator +
       "package-instance";



  /**
   * Make sure the server is running.
   *
   * @throws  Exception  If a problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
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
  @Test(expectedExceptions = { NullPointerException.class })
  public void testConstructorNullPIN()
         throws Exception
  {
    new CertificateManager(JKS_KEY_STORE_PATH, "JKS", null);
  }



  /**
   * Tests the CertificateManager constructor using an empty string for the key
   * store PIN.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(expectedExceptions = { NullPointerException.class })
  public void testConstructorEmptyPIN()
         throws Exception
  {
    new CertificateManager(JKS_KEY_STORE_PATH, "JKS", "");
  }



  /**
   * Tests the certificate manager with a valid constructor using the JKS key
   * store type.
   */
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
  public void testGenerateSelfSignedCertificateNullAlias()
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
      certManager.generateSelfSignedCertificate(null, "CN=Test,O=test", 365);
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an empty
   * alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateSelfSignedCertificateEmptyAlias()
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
      certManager.generateSelfSignedCertificate("", "CN=Test,O=test", 365);
      fail("Expected an NPE due to an empty alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an alias
   * that's already being used.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateSelfSignedCertificateAliasInUse()
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
      certManager.generateSelfSignedCertificate("server-cert", "CN=Test,O=test",
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
  @Test()
  public void testGenerateSelfSignedCertificateNullSubject()
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
      certManager.generateSelfSignedCertificate("test-cert", null, 365);
      fail("Expected an NPE due to a null subject");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an empty
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateSelfSignedCertificateEmptySubject()
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
      certManager.generateSelfSignedCertificate("test-cert", "", 365);
      fail("Expected an NPE due to an empty subject");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method using an invalid
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateSelfSignedCertificateInvalidSubject()
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
      certManager.generateSelfSignedCertificate("test-cert", "invalid", 365);
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
  @Test()
  public void testGenerateSelfSignedCertificateInvalidValidity()
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
      certManager.generateSelfSignedCertificate("test-cert", "CN=Test,o=test",
                                                0);
      fail("Expected an illegal argument exception due to an invalid validity");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code generateSelfSignedCertificate} method for a JKS key store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateSelfSignedCertificateJKS()
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
    certManager.generateSelfSignedCertificate("test-cert", "CN=Test,o=test",
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
  @Test(groups="slow")
  public void testGenerateSelfSignedCertificatePKCS12()
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
    certManager.generateSelfSignedCertificate("test-cert", "CN=Test,o=test",
                                              365);
    assertTrue(certManager.aliasInUse("test-cert"));
    path.delete();
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method using a null
   * alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSRNullAlias()
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
      certManager.generateCertificateSigningRequest(null, "CN=Test,O=test");
      fail("Expected an NPE due to a null alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method using an empty
   * alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSREmptyAlias()
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
      certManager.generateCertificateSigningRequest("", "CN=Test,O=test");
      fail("Expected an NPE due to an empty alias");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method using an alias
   * that's already being used.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSRAliasInUse()
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
      certManager.generateCertificateSigningRequest("server-cert",
                                                    "CN=Test,O=test");
      fail("Expected an illegal argument exception to a duplicate alias");
    } catch (IllegalArgumentException iae) {}
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method using a null
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSRNullSubject()
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
      certManager.generateCertificateSigningRequest("test-cert", null);
      fail("Expected an NPE due to a null subject");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method using an empty
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSREmptySubject()
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
      certManager.generateCertificateSigningRequest("test-cert", "");
      fail("Expected an NPE due to an empty subject");
    } catch (NullPointerException npe) {}
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method using an invalid
   * subject.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSRInvalidSubject()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testGenerateCSRJKS",
                                    ".keystore");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "JKS", "password");

    try
    {
      File requestFile =
           certManager.generateCertificateSigningRequest("test-cert",
                                                         "invalid");
      requestFile.delete();
      fail("Expected a key store exception due to an invalid subject");
    } catch (KeyStoreException cse) {}
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method for a JKS key
   * store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
  public void testGenerateCSRJKS()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testGenerateCSRJKS",
                                    ".keystore");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "JKS", "password");
    File csrFile = certManager.generateCertificateSigningRequest("test-cert",
                                    "CN=Test,o=test");
    assertNotNull(csrFile);
    assertTrue(csrFile.length() > 0);
    path.delete();
  }



  /**
   * Tests the {@code generateCertificateSigningRequest} method for a PKCS12 key
   * store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test(groups="slow")
  public void testGenerateCSRPKCS12()
         throws Exception
  {
    if (! CERT_MANAGER_AVAILABLE)
    {
      return;
    }

    File path = File.createTempFile("testGenerateCSRPKCS12",
                                    ".p12");
    path.deleteOnExit();
    path.delete();

    CertificateManager certManager =
         new CertificateManager(path.getAbsolutePath(), "PKCS12", "password");
    File csrFile = certManager.generateCertificateSigningRequest("test-cert",
                                    "CN=Test,o=test");
    assertNotNull(csrFile);
    assertTrue(csrFile.length() > 0);
    path.delete();
  }



  /**
   * Tests the {@code addCertificate} method using a null alias.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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
  @Test()
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

