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
package org.opends.quicksetup;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import org.opends.quicksetup.util.ZipExtractor;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.util.CertificateFixture;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.OperatingSystem;

/**
 * Installs a server with {@code setup} and the arguments which provision the trust store
 * used for server to server communication, and checks what the installed instance holds:
 * this is the whole point of the arguments, and the pieces which carry the values from
 * the command line to the trust store and to the crypto manager sit in three different
 * tools.
 */
public class AdsTrustStoreInstallTestCase extends DirectoryServerTestCase
{
  private static final String KEY_STORE_PASSWORD = "keyStorePassword";
  private static final String CERT_NICKNAME = "server-cert";

  /**
   * The installed server presents the CA-signed key pair on the replication port and
   * trusts the authority which issued it, with no manual {@code keytool} pass.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testSetupProvisionsTheAdsTrustStore() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreInstall");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File keyStore = new File(workspace, "server.p12");
      ca.addKeyEntry(keyStore, "PKCS12", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", true);

      final File serverRoot = installServer(workspace, keyStore, "--useKeyStoreForReplication");

      final KeyStore keys = loadAdsTrustStore(serverRoot);
      assertTrue(keys.isKeyEntry(CERT_NICKNAME), "the key pair to present was not imported");
      assertEquals(keys.getCertificateChain(CERT_NICKNAME).length, 2);
      assertEquals(keys.getCertificateAlias(ca.getCaCertificate()), "ads-ca-1",
          "the issuing certificate is not trusted");

      assertEquals(cryptoManagerCertNicknames(serverRoot), List.of(CERT_NICKNAME),
          "the crypto manager still presents another certificate");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * A key store which holds the issued certificate alone is enough when the certificate of
   * the authority is named separately.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testSetupTrustsTheNamedCaCertificate() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreCaFile");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File keyStore = new File(workspace, "server.p12");
      ca.addKeyEntry(keyStore, "PKCS12", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", false);
      final File caFile = new File(workspace, "ca.crt");
      ca.writeCaCertificate(caFile);

      final File serverRoot = installServer(workspace, keyStore,
          "--useKeyStoreForReplication", "--replicationCaCertFile", caFile.getAbsolutePath());

      final KeyStore keys = loadAdsTrustStore(serverRoot);
      assertTrue(keys.isKeyEntry(CERT_NICKNAME), "the key pair to present was not imported");
      assertEquals(keys.getCertificateAlias(ca.getCaCertificate()), "ads-ca-1",
          "the named certificate is not trusted");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * A key store which holds the issued certificate alone, with no certificate to trust
   * named either, would install a server which trusts no peer.  The installation stops
   * and says which key pair is at fault, rather than leave the failure to show up as a
   * handshake error once the server joins a topology.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testSetupRefusesAKeyStoreWithNoCertificateToTrust() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreNoAnchor");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File keyStore = new File(workspace, "server.p12");
      ca.addKeyEntry(keyStore, "PKCS12", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", false);

      final SetupResult result = runSetup(workspace, keyStore, "--useKeyStoreForReplication");
      assertNotEquals(result.exitCode, 0, "setup installed a server which trusts no peer:\n" + result.output);
      assertTrue(result.output.contains(CERT_NICKNAME), result.output);
      assertTrue(result.output.contains("--replicationCaCertFile"), result.output);
      assertFalse(new File(result.serverRoot, "config" + File.separator + "ads-truststore").exists(),
          "a trust store was left behind");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * Without the arguments, the installation is left as it was: the trust store is created
   * by the server on its first start and the crypto manager keeps presenting the
   * self-signed key pair the trust store backend generates.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testSetupLeavesTheSelfSignedInstanceKeyByDefault() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreDefault");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File keyStore = new File(workspace, "server.p12");
      ca.addKeyEntry(keyStore, "PKCS12", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", true);

      final File serverRoot = installServer(workspace, keyStore);

      assertFalse(new File(serverRoot, "config" + File.separator + "ads-truststore").exists(),
          "the trust store is provisioned without being asked for");
      assertEquals(cryptoManagerCertNicknames(serverRoot), List.of("ads-certificate"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /** Loads the provisioned trust store with the PIN setup wrote for it. */
  private KeyStore loadAdsTrustStore(File serverRoot) throws Exception
  {
    final File trustStore = new File(serverRoot, "config" + File.separator + "ads-truststore");
    final File pinFile = new File(serverRoot, "config" + File.separator + "ads-truststore.pin");
    assertTrue(trustStore.exists(), "setup left no " + trustStore);
    assertTrue(pinFile.exists(), "setup left no " + pinFile);

    final KeyStore keys = KeyStore.getInstance("JKS");
    final String pin = new String(Files.readAllBytes(pinFile.toPath()), StandardCharsets.UTF_8).trim();
    try (final FileInputStream in = new FileInputStream(trustStore))
    {
      keys.load(in, pin.toCharArray());
    }
    return keys;
  }

  /** Extracts the built package and runs setup on it, returning the server root. */
  private File installServer(File workspace, File keyStore, String... extraArgs) throws Exception
  {
    final SetupResult result = runSetup(workspace, keyStore, extraArgs);
    assertEquals(result.exitCode, 0, "setup failed:\n" + result.output);
    return result.serverRoot;
  }

  /** Extracts the built package and runs setup on it, whether it succeeds or not. */
  private SetupResult runSetup(File workspace, File keyStore, String... extraArgs) throws Exception
  {
    final File serverRoot = new File(workspace, "opendj");
    new ZipExtractor(TestUtilities.getInstallPackageFile()).extract(serverRoot);

    final int[] ports = TestCaseUtils.findFreePorts(3);
    final List<String> args = new ArrayList<>();
    args.add(new File(serverRoot, OperatingSystem.isWindows() ? "setup.bat" : "setup").getPath());
    args.add("--cli");
    args.add("-n");
    args.add("-O");
    args.add("-w");
    args.add("password");
    args.add("-b");
    args.add("dc=example,dc=com");
    args.add("-p");
    args.add(String.valueOf(ports[0]));
    args.add("--adminConnectorPort");
    args.add(String.valueOf(ports[1]));
    args.add("-x");
    args.add(String.valueOf(ports[2]));
    args.add("--usePkcs12keyStore");
    args.add(keyStore.getAbsolutePath());
    args.add("--keyStorePassword");
    args.add(KEY_STORE_PASSWORD);
    args.add("--certNickname");
    args.add(CERT_NICKNAME);
    args.add("--enableStartTLS");
    args.addAll(List.of(extraArgs));

    final Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
    final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new SetupResult(serverRoot, process.waitFor(), output);
  }

  /** What one run of the setup command produced. */
  private static final class SetupResult
  {
    private final File serverRoot;
    private final int exitCode;
    private final String output;

    private SetupResult(File serverRoot, int exitCode, String output)
    {
      this.serverRoot = serverRoot;
      this.exitCode = exitCode;
      this.output = output;
    }
  }

  /** Returns the ssl-cert-nickname values of the crypto manager entry of the written configuration. */
  private List<String> cryptoManagerCertNicknames(File serverRoot) throws Exception
  {
    final File configFile = new File(serverRoot, "config" + File.separator + "config.ldif");
    final List<String> nicknames = new ArrayList<>();
    boolean inCryptoManager = false;
    for (String line : Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8))
    {
      if (line.isEmpty())
      {
        inCryptoManager = false;
      }
      else if (line.equalsIgnoreCase("dn: cn=Crypto Manager,cn=config"))
      {
        inCryptoManager = true;
      }
      else if (inCryptoManager && line.startsWith("ds-cfg-ssl-cert-nickname:"))
      {
        nicknames.add(line.substring("ds-cfg-ssl-cert-nickname:".length()).trim());
      }
    }
    return nicknames;
  }
}
