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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.quicksetup.installer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import com.forgerock.opendj.util.OperatingSystem;
import org.opends.quicksetup.ApplicationException;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.util.CertificateFixture;
import org.opends.server.util.CertificateManager;
import org.testng.annotations.Test;

/**
 * Tests the provisioning of the {@code ads-truststore} from a key store an operator
 * already holds, which is what lets {@code setup} secure the replication port with a
 * CA-signed certificate instead of the self-signed {@code ads-certificate}.
 */
public class AdsTrustStoreProvisionerTest extends DirectoryServerTestCase
{
  private static final String SOURCE_PASSWORD = "sourcePassword";

  /**
   * The key pair is imported with its chain, the issuers of that chain are trusted, and
   * the generated PIN is the one written to the PIN file: a PIN file which does not open
   * the trust store leaves the server unable to read it.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testProvisionImportsKeyPairIssuersAndWritesPin() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("provisionAdsTrustStore");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert", "CN=host.example.com", true);

      final File trustStore = new File(tmpDir, "ads-truststore");
      final File pinFile = new File(tmpDir, "ads-truststore.pin");
      newProvisioner(trustStore, pinFile).provision(
          sourceManager(source), Collections.singletonList("server-cert"), Collections.<File> emptyList());

      final KeyStore keyStore = loadTrustStore(trustStore, pinFile);
      assertTrue(keyStore.isKeyEntry("server-cert"));
      assertEquals(keyStore.getCertificateChain("server-cert").length, 2);
      assertNotNull(keyStore.getKey("server-cert", pinOf(pinFile).toCharArray()));
      assertEquals(keyStore.getCertificateAlias(ca.getCaCertificate()), "ads-ca-1");
      assertTrue(keyStore.isCertificateEntry("ads-ca-1"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * The instance key of the server is not provisioned: the server generates
   * {@code ads-certificate} itself when it first starts, and it has to stay the key pair
   * published to the topology as the crypto manager instance key.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testProvisionLeavesTheInstanceKeyToTheServer() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("provisionInstanceKey");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert", "CN=host.example.com", true);

      final File trustStore = new File(tmpDir, "ads-truststore");
      final File pinFile = new File(tmpDir, "ads-truststore.pin");
      newProvisioner(trustStore, pinFile).provision(
          sourceManager(source), Collections.singletonList("server-cert"), Collections.<File> emptyList());

      assertFalse(loadTrustStore(trustStore, pinFile).containsAlias("ads-certificate"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * A key store which holds the issued certificate alone gives the trust store no trust
   * anchor: the trust managers take the certificate a key belongs to and none of its
   * issuers, so such a server would trust no peer. The provisioning reports it and leaves
   * no half-written trust store behind.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testProvisionFailsWithoutTrustAnchorAndWritesNothing() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("provisionNoAnchor");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert", "CN=host.example.com", false);

      final File trustStore = new File(tmpDir, "ads-truststore");
      final File pinFile = new File(tmpDir, "ads-truststore.pin");
      try
      {
        newProvisioner(trustStore, pinFile).provision(
            sourceManager(source), Collections.singletonList("server-cert"), Collections.<File> emptyList());
        fail("Expected the provisioning to report that no certificate is trusted");
      }
      catch (ApplicationException e)
      {
        assertTrue(e.getMessageObject().toString().contains("server-cert"), e.getMessageObject().toString());
      }
      assertFalse(trustStore.exists());
      assertFalse(pinFile.exists());
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * A key store which holds the issued certificate alone is enough when the certificates
   * to trust are provided separately, as the issuing certificate is then imported from
   * its file.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testProvisionTrustsCaCertificateFromFile() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("provisionCaFile");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert", "CN=host.example.com", false);
      final File caFile = new File(tmpDir, "ca.crt");
      ca.writeCaCertificate(caFile);

      final File trustStore = new File(tmpDir, "ads-truststore");
      final File pinFile = new File(tmpDir, "ads-truststore.pin");
      newProvisioner(trustStore, pinFile).provision(
          sourceManager(source), Collections.singletonList("server-cert"), Collections.singletonList(caFile));

      final KeyStore keyStore = loadTrustStore(trustStore, pinFile);
      assertTrue(keyStore.isKeyEntry("server-cert"));
      assertTrue(keyStore.isCertificateEntry("ads-ca-1"));
      assertEquals(keyStore.getCertificate("ads-ca-1"), ca.getCaCertificate());
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * Two key pairs issued by the same authority, as the RSA and EC pairs of one server
   * are, trust that authority once rather than under one alias each.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testProvisionTrustsASharedIssuerOnce() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("provisionSharedIssuer");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert", "CN=host.example.com", true);
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert-ec", "CN=host.example.com", true);

      final File trustStore = new File(tmpDir, "ads-truststore");
      final File pinFile = new File(tmpDir, "ads-truststore.pin");
      newProvisioner(trustStore, pinFile).provision(
          sourceManager(source), Arrays.asList("server-cert", "server-cert-ec"), Collections.<File> emptyList());

      final KeyStore keyStore = loadTrustStore(trustStore, pinFile);
      assertTrue(keyStore.isKeyEntry("server-cert"));
      assertTrue(keyStore.isKeyEntry("server-cert-ec"));
      assertTrue(keyStore.isCertificateEntry("ads-ca-1"));
      assertFalse(keyStore.containsAlias("ads-ca-2"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * The PIN file is readable by its owner only, as it opens the private key presented on
   * the replication port.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testProvisionProtectsThePinFile() throws Exception
  {
    if (OperatingSystem.isWindows())
    {
      return;
    }

    final File tmpDir = TestCaseUtils.createTemporaryDirectory("provisionPinPermissions");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File source = new File(tmpDir, "server.p12");
      ca.addKeyEntry(source, "PKCS12", SOURCE_PASSWORD, "server-cert", "CN=host.example.com", true);

      final File trustStore = new File(tmpDir, "ads-truststore");
      final File pinFile = new File(tmpDir, "ads-truststore.pin");
      newProvisioner(trustStore, pinFile).provision(
          sourceManager(source), Collections.singletonList("server-cert"), Collections.<File> emptyList());

      final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(pinFile.toPath());
      assertEquals(permissions, PosixFilePermissions.fromString("rw-------"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  private AdsTrustStoreProvisioner newProvisioner(File trustStore, File pinFile)
  {
    return new AdsTrustStoreProvisioner(trustStore.getAbsolutePath(), pinFile.getAbsolutePath());
  }

  private CertificateManager sourceManager(File source)
  {
    return new CertificateManager(source.getAbsolutePath(), CertificateManager.KEY_STORE_TYPE_PKCS12,
        SOURCE_PASSWORD);
  }

  private String pinOf(File pinFile) throws Exception
  {
    return new String(Files.readAllBytes(pinFile.toPath()), StandardCharsets.UTF_8).trim();
  }

  private KeyStore loadTrustStore(File trustStore, File pinFile) throws Exception
  {
    final KeyStore keyStore = KeyStore.getInstance(CertificateManager.KEY_STORE_TYPE_JKS);
    try (final FileInputStream in = new FileInputStream(trustStore))
    {
      keyStore.load(in, pinOf(pinFile).toCharArray());
    }
    return keyStore;
  }
}
