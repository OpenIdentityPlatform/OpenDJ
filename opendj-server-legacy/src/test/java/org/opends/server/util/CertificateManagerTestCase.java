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
 * Portions Copyright 2026 3A Systems, LLC
 */
package org.opends.server.util;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.security.auth.module.UnixSystem;

import org.testng.SkipException;
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
//  public static final String BUILD_ROOT =
//          System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);


  /**
   * The path to a JKS key store file.
   */
  public static final String JKS_KEY_STORE_PATH() {
	  return TestCaseUtils.paths.testInstanceRoot.getPath() + File.separator + "config" + File.separator + "server.keystore";
  }
  


  /**
   * The path to a PKCS#12 key store file.
   */
  public static final String PKCS12_KEY_STORE_PATH() {
	  return TestCaseUtils.paths.testInstanceRoot.getPath() + File.separator + "config" + File.separator + "server-cert.p12";
  }



  /**
   * The path to the unit test working directory.
   */
  public static final String TEST_DIR() {
	  return TestCaseUtils.paths.testInstanceRoot.getPath();
  }



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
    String path = TEST_DIR() + File.separator + "nonexistent" + File.separator +
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
    new CertificateManager(TEST_DIR(), "JKS", "password");
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
    new CertificateManager(JKS_KEY_STORE_PATH(), null, "password");
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
    new CertificateManager(JKS_KEY_STORE_PATH(), "", "password");
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
    new CertificateManager(JKS_KEY_STORE_PATH(), "invalid", "password");
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
    new CertificateManager(JKS_KEY_STORE_PATH(), "PKCS11", "password");
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
    assertNotNull(new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", (String) null));
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
    assertNotNull(new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", ""));
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

    new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");
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

    new CertificateManager(PKCS12_KEY_STORE_PATH(), "PKCS12", "password");
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");
    assertTrue(certManager.aliasInUse("server-cert"));
    assertFalse(certManager.aliasInUse("nonexistent"));

    String path = TEST_DIR() + File.separator + "nonexistent";
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
         new CertificateManager(PKCS12_KEY_STORE_PATH(), "PKCS12", "password");
    assertTrue(certManager.aliasInUse("server-cert"));
    assertFalse(certManager.aliasInUse("nonexistent"));

    String path = TEST_DIR() + File.separator + "nonexistent";
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

    String[] aliases = certManager.getCertificateAliases();
    assertNotNull(aliases);
    assertTrue(aliases.length > 0);
    assertTrue(Arrays.asList(aliases).contains("server-cert"));

    String path = TEST_DIR() + File.separator + "nonexistent";
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
         new CertificateManager(PKCS12_KEY_STORE_PATH(), "PKCS12", "password");

    String[] aliases = certManager.getCertificateAliases();
    assertNotNull(aliases);
    assertTrue(aliases.length > 0);
    assertTrue(Arrays.asList(aliases).contains("server-cert"));

    String path = TEST_DIR() + File.separator + "nonexistent";
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");
    assertNotNull(certManager.getCertificate("server-cert"));
    assertNull(certManager.getCertificate("nonexistent"));

    String path = TEST_DIR() + File.separator + "nonexistent";
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
         new CertificateManager(PKCS12_KEY_STORE_PATH(), "PKCS12", "password");
    assertNotNull(certManager.getCertificate("server-cert"));
    assertNull(certManager.getCertificate("nonexistent"));

    String path = TEST_DIR() + File.separator + "nonexistent";
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
  @Test(dataProvider="keyTypes")
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

    File f = new File(TEST_DIR(), "nonexistent");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

    File f = new File(TEST_DIR());

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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
   * Tests that {@code importKeyEntry} copies the whole certificate chain of the source
   * key entry and re-encrypts the private key with the password of the destination key
   * store: the key managers of the server are initialised with the store password only,
   * so a key which kept the password of the source key store could not be read back.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testImportKeyEntryCopiesChainAndReEncryptsKey()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("importKeyEntry");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", "sourcePassword", "server-cert", "CN=host.example.com", true);

      final File destination = new File(tmpDir, "ads-truststore");
      final CertificateManager destinationManager =
           new CertificateManager(destination.getAbsolutePath(), "JKS", "destinationPassword");
      destinationManager.importKeyEntry("server-cert",
           new CertificateManager(source.getAbsolutePath(), "PKCS12", "sourcePassword"), "server-cert");

      final KeyStore keyStore = KeyStore.getInstance("JKS");
      try (final FileInputStream in = new FileInputStream(destination))
      {
        keyStore.load(in, "destinationPassword".toCharArray());
      }
      assertTrue(keyStore.isKeyEntry("server-cert"));
      assertNotNull(keyStore.getKey("server-cert", "destinationPassword".toCharArray()));
      assertEquals(keyStore.getCertificateChain("server-cert").length, 2);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that {@code importKeyEntry} reports an alias which the source key store does
   * not hold, rather than silently importing nothing.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testImportKeyEntryNonexistentSourceAlias()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("importKeyEntryMissing");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", "sourcePassword", "server-cert", "CN=host.example.com", true);

      final CertificateManager destinationManager = new CertificateManager(
           new File(tmpDir, "ads-truststore").getAbsolutePath(), "JKS", "destinationPassword");
      try
      {
        destinationManager.importKeyEntry("nonexistent",
             new CertificateManager(source.getAbsolutePath(), "PKCS12", "sourcePassword"), "nonexistent");
        fail("Expected a key store exception due to a nonexistent source alias");
      } catch (KeyStoreException kse) {}
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that {@code getCertificateChain} returns the issuers of a key entry, which is
   * where the certificates to trust are taken from when a key pair is provisioned.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testGetCertificateChainReturnsIssuers()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("getCertificateChain");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", "sourcePassword", "server-cert", "CN=host.example.com", true);

      final CertificateManager sourceManager =
           new CertificateManager(source.getAbsolutePath(), "PKCS12", "sourcePassword");
      final Certificate[] chain = sourceManager.getCertificateChain("server-cert");
      assertEquals(chain.length, 2);
      assertEquals(chain[1], ca.getCaCertificate());
      assertNull(sourceManager.getCertificateChain("nonexistent"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that {@code addTrustedCertificate} stores a certificate held in memory as a
   * trusted certificate entry: only such an entry is a trust anchor, the certificate
   * chain of a key entry is not.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testAddTrustedCertificateStoresTrustAnchor()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("addTrustedCertificate");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File destination = new File(tmpDir, "ads-truststore");

      final CertificateManager destinationManager =
           new CertificateManager(destination.getAbsolutePath(), "JKS", "destinationPassword");
      destinationManager.addTrustedCertificate("ads-ca-1", ca.getCaCertificate());

      final KeyStore keyStore = KeyStore.getInstance("JKS");
      try (final FileInputStream in = new FileInputStream(destination))
      {
        keyStore.load(in, "destinationPassword".toCharArray());
      }
      assertTrue(keyStore.isCertificateEntry("ads-ca-1"));
      assertEquals(keyStore.getCertificate("ads-ca-1"), ca.getCaCertificate());
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that {@code importKeyEntry} names the cause when the private key is protected
   * by a password of its own: the key managers of the server unlock keys with the store
   * password only, and "Cannot recover key" says neither that nor what to do about it.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testImportKeyEntryReportsAKeyPasswordWhichDiffers()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("importKeyEntryKeyPassword");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.jks");
      ca.addKeyEntry(source, "JKS", "sourcePassword", "keyPassword", "server-cert", "CN=host.example.com", true);

      final File destination = new File(tmpDir, "ads-truststore");
      final CertificateManager destinationManager =
           new CertificateManager(destination.getAbsolutePath(), "JKS", "destinationPassword");
      try
      {
        destinationManager.importKeyEntry("server-cert",
             new CertificateManager(source.getAbsolutePath(), "JKS", "sourcePassword"), "server-cert");
        fail("Expected a key store exception due to a key password which differs from the store password");
      }
      catch (KeyStoreException kse)
      {
        assertTrue(kse.getMessage().contains("server-cert"), kse.getMessage());
        assertTrue(kse.getMessage().contains(source.getAbsolutePath()), kse.getMessage());
        assertTrue(kse.getMessage().contains("password"), kse.getMessage());
        assertFalse(kse.getMessage().contains("Cannot recover key"), kse.getMessage());
      }
      assertFalse(destination.exists(), "a destination key store was written for a key which could not be read");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that neither {@code importKeyEntry} nor {@code addTrustedCertificate} replaces
   * an entry which is already there, as {@code generateSelfSignedCertificate} and
   * {@code addCertificate} do not: the key store methods they wrap overwrite silently.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testImportKeyEntryAndAddTrustedCertificateRefuseAnAliasInUse()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("aliasInUse");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", "sourcePassword", "server-cert", "CN=host.example.com", true);
      ca.addKeyEntry(source, "PKCS12", "sourcePassword", "other-cert", "CN=other.example.com", true);
      final CertificateManager sourceManager =
           new CertificateManager(source.getAbsolutePath(), "PKCS12", "sourcePassword");

      final File destination = new File(tmpDir, "ads-truststore");
      final CertificateManager destinationManager =
           new CertificateManager(destination.getAbsolutePath(), "JKS", "destinationPassword");
      destinationManager.importKeyEntry("server-cert", sourceManager, "server-cert");
      destinationManager.addTrustedCertificate("ads-ca-1", ca.getCaCertificate());

      try
      {
        destinationManager.importKeyEntry("server-cert", sourceManager, "other-cert");
        fail("Expected a key store exception due to a key entry alias already in use");
      }
      catch (KeyStoreException kse)
      {
        assertTrue(kse.getMessage().contains("server-cert"), kse.getMessage());
      }
      try
      {
        destinationManager.addTrustedCertificate("ads-ca-1", sourceManager.getCertificate("other-cert"));
        fail("Expected a key store exception due to a trusted certificate alias already in use");
      }
      catch (KeyStoreException kse)
      {
        assertTrue(kse.getMessage().contains("ads-ca-1"), kse.getMessage());
      }
      try
      {
        destinationManager.importKeyEntry("ads-ca-1", sourceManager, "other-cert");
        fail("Expected a key store exception due to an alias held by a trusted certificate");
      }
      catch (KeyStoreException kse)
      {
        assertTrue(kse.getMessage().contains("ads-ca-1"), kse.getMessage());
      }

      final KeyStore keyStore = KeyStore.getInstance("JKS");
      try (final FileInputStream in = new FileInputStream(destination))
      {
        keyStore.load(in, "destinationPassword".toCharArray());
      }
      assertEquals(keyStore.getCertificate("server-cert"), sourceManager.getCertificate("server-cert"),
           "the key entry was replaced");
      assertEquals(keyStore.getCertificate("ads-ca-1"), ca.getCaCertificate(), "the trusted certificate was replaced");
      assertEquals(keyStore.size(), 2);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that a reader of a key store file never finds it half written while it is rewritten:
   * the trust store of the server is read from its file for each SSL context, and at the next
   * start, by readers which do not share any lock with the writer, and a server stopped part way
   * through a write must not be left with a truncated file it cannot start with (#1188). On a
   * POSIX file system only: Windows does not replace a file a reader holds open, and the key
   * store is then written in place.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreFileIsNeverSeenHalfWritten()
         throws Exception
  {
    skipUnlessPosix();
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("halfWritten");
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final AtomicBoolean writing = new AtomicBoolean(true);
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      final File destination = new File(tmpDir, "ads-truststore");
      final CertificateManager manager =
           new CertificateManager(destination.getAbsolutePath(), "JKS", "password");
      manager.addTrustedCertificate("cert-0", certificate);

      final CountDownLatch started = new CountDownLatch(1);
      final AtomicInteger loads = new AtomicInteger();
      final Future<String> reads = executor.submit(() -> {
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        started.countDown();
        while (writing.get())
        {
          try (FileInputStream in = new FileInputStream(destination))
          {
            keyStore.load(in, "password".toCharArray());
            loads.incrementAndGet();
          }
          catch (Exception e)
          {
            return e.toString();
          }
        }
        return null;
      });
      assertTrue(started.await(60, TimeUnit.SECONDS), "the reader started");
      for (int i = 1; i <= 200; i++)
      {
        manager.addTrustedCertificate("cert-" + i, certificate);
      }
      writing.set(false);
      assertNull(reads.get(60, TimeUnit.SECONDS), "a read of the key store while it was rewritten");
      assertTrue(loads.get() > 0, "the reader loaded the key store while it was rewritten");
    }
    finally
    {
      writing.set(false);
      executor.shutdownNow();
      executor.awaitTermination(60, TimeUnit.SECONDS);
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that the rewrite of a key store keeps the file it replaces as it was: its permissions,
   * which the file it is written to first does not have, and the link the key store path may be,
   * which must go on pointing where the administrator put the key store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreRewriteKeepsPermissionsAndLink()
         throws Exception
  {
    skipUnlessPosix();
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("rewriteKeeps");
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      final Path store = Files.createDirectory(tmpDir.toPath().resolve("store"));
      final Path target = store.resolve("ads-truststore");
      new CertificateManager(target.toString(), "JKS", "password").addTrustedCertificate("cert-0", certificate);
      Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r-----"));
      final Path link = Files.createSymbolicLink(tmpDir.toPath().resolve("ads-truststore"), target);

      new CertificateManager(link.toString(), "JKS", "password").addTrustedCertificate("cert-1", certificate);

      assertTrue(Files.isSymbolicLink(link), "the key store path is a link still");
      assertEquals(PosixFilePermissions.toString(Files.getPosixFilePermissions(target)), "rw-r-----");
      assertTrue(loadJks(target.toFile(), "password").containsAlias("cert-1"), "the key store the link points to");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that a key store in a directory this process cannot write to, as one mounted on its own
   * into a container, is still rewritten: in place, there being nowhere else to write it.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreInADirectoryNotWritableIsRewrittenInPlace()
         throws Exception
  {
    skipUnlessPosix();
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("rewriteInPlace");
    final Path store = tmpDir.toPath().resolve("store");
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      Files.createDirectory(store);
      final Path target = store.resolve("ads-truststore");
      final CertificateManager manager = new CertificateManager(target.toString(), "JKS", "password");
      manager.addTrustedCertificate("cert-0", certificate);
      Files.setPosixFilePermissions(store, PosixFilePermissions.fromString("r-xr-xr-x"));
      if (Files.isWritable(store))
      {
        throw new SkipException("this process writes to a directory whatever its permissions");
      }

      manager.addTrustedCertificate("cert-1", certificate);

      assertTrue(loadJks(target.toFile(), "password").containsAlias("cert-1"), "the key store");
    }
    finally
    {
      if (Files.exists(store))
      {
        Files.setPosixFilePermissions(store, PosixFilePermissions.fromString("rwxr-xr-x"));
      }
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that the rewrite of a key store keeps its group, a group of this process other than the
   * one a new file gets, and still replaces the file at once rather than writing it in place.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreRewriteKeepsTheGroup()
         throws Exception
  {
    skipUnlessPosix();
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("rewriteKeepsGroup");
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      final Path target = tmpDir.toPath().resolve("ads-truststore");
      new CertificateManager(target.toString(), "JKS", "password").addTrustedCertificate("cert-0", certificate);
      final long newFileGroup = ((Integer) Files.getAttribute(target, "unix:gid")).longValue();
      final UnixSystem process = new UnixSystem();
      long group = process.getGid();
      for (long g : process.getGroups())
      {
        if (group == newFileGroup)
        {
          group = g;
        }
      }
      if (group == newFileGroup)
      {
        throw new SkipException("this process is a member of no group but the one a new file gets");
      }
      Files.getFileAttributeView(target, PosixFileAttributeView.class).setGroup(FileSystems.getDefault()
          .getUserPrincipalLookupService().lookupPrincipalByGroupName(String.valueOf(group)));
      final Object file = Files.getAttribute(target, "unix:ino");

      new CertificateManager(target.toString(), "JKS", "password").addTrustedCertificate("cert-1", certificate);

      assertEquals(((Integer) Files.getAttribute(target, "unix:gid")).longValue(), group,
          "the group of the key store");
      assertNotEquals(Files.getAttribute(target, "unix:ino"), file, "the key store replaced, not written in place");
      assertTrue(loadJks(target.toFile(), "password").containsAlias("cert-1"), "the key store");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that a key store file with another hard link is rewritten in place: a new file in its
   * place would leave the other name with the old key store.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreWithAnotherHardLinkIsRewrittenInPlace()
         throws Exception
  {
    skipUnlessPosix();
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("rewriteHardLink");
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      final Path target = tmpDir.toPath().resolve("ads-truststore");
      new CertificateManager(target.toString(), "JKS", "password").addTrustedCertificate("cert-0", certificate);
      final Path other = Files.createLink(tmpDir.toPath().resolve("ads-truststore.link"), target);

      new CertificateManager(target.toString(), "JKS", "password").addTrustedCertificate("cert-1", certificate);

      assertTrue(loadJks(other.toFile(), "password").containsAlias("cert-1"), "the key store by its other name");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that a key store file this process may not write to is not rewritten, even in a
   * directory it may write to, where the file could be replaced.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreNotWritableIsRefused()
         throws Exception
  {
    skipUnlessPosix();
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("rewriteRefused");
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      final Path target = tmpDir.toPath().resolve("ads-truststore");
      final CertificateManager manager = new CertificateManager(target.toString(), "JKS", "password");
      manager.addTrustedCertificate("cert-0", certificate);
      Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"));
      if (Files.isWritable(target))
      {
        throw new SkipException("this process writes to a file whatever its permissions");
      }

      assertThrows(KeyStoreException.class, () -> manager.addTrustedCertificate("cert-1", certificate));

      assertFalse(loadJks(target.toFile(), "password").containsAlias("cert-1"), "the key store");
      assertEquals(PosixFilePermissions.toString(Files.getPosixFilePermissions(target)), "r--r--r--");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  /**
   * Tests that a key store which cannot be replaced at once, as a file mounted on its own into a
   * container (EBUSY, EXDEV) or one a reader holds open on Windows, is still rewritten: in place.
   *
   * @throws  Exception  If a problem occurs.
   */
  @Test
  public void testKeyStoreWhichCannotBeReplacedIsRewrittenInPlace()
         throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("rewriteNotReplaced");
    try
    {
      final Certificate certificate = new CertificateFixture("CN=Example CA,O=Example").getCaCertificate();
      final File target = new File(tmpDir, "ads-truststore");
      final CertificateManager manager = new CertificateManager(target.getAbsolutePath(), "JKS", "password");
      manager.addTrustedCertificate("cert-0", certificate);

      try (AutoCloseable inPlace = KeyStoreReplaceFailure.failKeyStoreReplace())
      {
        manager.addTrustedCertificate("cert-1", certificate);
      }

      assertTrue(loadJks(target, "password").containsAlias("cert-1"), "the key store");
      assertEquals(tmpDir.list(), new String[] { "ads-truststore" }, "the files next to the key store");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }



  private static void skipUnlessPosix()
  {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
    {
      throw new SkipException("POSIX file permissions and links are not available here");
    }
  }



  private static KeyStore loadJks(File file, String password) throws Exception
  {
    final KeyStore keyStore = KeyStore.getInstance("JKS");
    try (FileInputStream in = new FileInputStream(file))
    {
      keyStore.load(in, password.toCharArray());
    }
    return keyStore;
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
         new CertificateManager(JKS_KEY_STORE_PATH(), "JKS", "password");

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

