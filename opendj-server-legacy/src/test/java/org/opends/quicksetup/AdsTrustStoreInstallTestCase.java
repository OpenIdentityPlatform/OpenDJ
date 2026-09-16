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
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.opends.quicksetup.util.ServerController;
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
 * <p>
 * Each installation uses a different key store type, so that every arm of the mapping
 * from the key store argument to the key store type is walked by one of them.
 */
public class AdsTrustStoreInstallTestCase extends DirectoryServerTestCase
{
  private static final String KEY_STORE_PASSWORD = "keyStorePassword";
  private static final String CERT_NICKNAME = "server-cert";
  private static final String SECOND_CERT_NICKNAME = "server-cert-2";
  /** How long one run of setup, or one stop of the server it started, is given. */
  private static final long TIMEOUT_MINUTES = 5;

  /**
   * The installed server presents the CA-signed key pairs on the replication port and
   * trusts the authority which issued them, with no manual {@code keytool} pass. Two key
   * pairs are named, so that both nicknames reach the crypto manager.
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
      ca.addKeyEntry(keyStore, "PKCS12", KEY_STORE_PASSWORD, SECOND_CERT_NICKNAME, "CN=host.example.com", true);

      final File serverRoot = installServer(workspace, "--usePkcs12keyStore", keyStore, "-O",
          "--certNickname", CERT_NICKNAME, "--certNickname", SECOND_CERT_NICKNAME, "--useKeyStoreForReplication");

      final KeyStore keys = loadAdsTrustStore(serverRoot);
      assertTrue(keys.isKeyEntry(CERT_NICKNAME), "the key pair to present was not imported");
      assertTrue(keys.isKeyEntry(SECOND_CERT_NICKNAME), "the second key pair to present was not imported");
      assertEquals(keys.getCertificateChain(CERT_NICKNAME).length, 2);
      assertEquals(keys.getCertificateAlias(ca.getCaCertificate()), "ads-ca-1",
          "the issuing certificate is not trusted");
      assertFalse(keys.containsAlias("ads-ca-2"), "the shared issuer is trusted twice");

      assertEquals(cryptoManagerCertNicknames(serverRoot), List.of(CERT_NICKNAME, SECOND_CERT_NICKNAME),
          "the crypto manager does not present both key pairs");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * A key store which holds the issued certificate alone is enough when the certificates
   * of the authorities are named separately, and every certificate of the named file is
   * trusted. With no nickname given, the only key pair of the key store is the one
   * presented.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testSetupTrustsTheNamedCaCertificates() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreCaFile");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final CertificateFixture otherCa = new CertificateFixture("CN=Other CA,O=Example");
      final File keyStore = new File(workspace, "server.jks");
      ca.addKeyEntry(keyStore, "JKS", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", false);
      final File caChainFile = new File(workspace, "ca-chain.crt");
      CertificateFixture.writeCertificates(caChainFile, ca.getCaCertificate(), otherCa.getCaCertificate());

      final File serverRoot = installServer(workspace, "--useJavaKeystore", keyStore, "-O",
          "--useKeyStoreForReplication", "--replicationCaCertFile", caChainFile.getAbsolutePath());

      final KeyStore keys = loadAdsTrustStore(serverRoot);
      assertTrue(keys.isKeyEntry(CERT_NICKNAME), "the key pair to present was not imported");
      assertEquals(keys.getCertificateAlias(ca.getCaCertificate()), "ads-ca-1",
          "the first named certificate is not trusted");
      assertEquals(keys.getCertificateAlias(otherCa.getCaCertificate()), "ads-ca-2",
          "the second certificate of the named file is not trusted");
      assertEquals(cryptoManagerCertNicknames(serverRoot), List.of(CERT_NICKNAME),
          "the only key pair of the key store is not the one presented");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * A key store which holds the issued certificate alone, with no certificate to trust
   * named either, would install a server which trusts no peer. The installation stops
   * and says which key pair is at fault, rather than leave the failure to show up as a
   * handshake error once the server joins a topology, and it stops before the
   * configuration and the certificates of the server are written.
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
      final File keyStore = new File(workspace, "server.jceks");
      ca.addKeyEntry(keyStore, "JCEKS", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", false);

      final SetupResult result = runSetup(workspace, "--useJCEKS", keyStore, "-O",
          "--certNickname", CERT_NICKNAME, "--useKeyStoreForReplication");
      assertNotEquals(result.exitCode, 0, "setup installed a server which trusts no peer:\n" + result.output);
      assertTrue(result.output.contains(CERT_NICKNAME), result.output);
      assertTrue(result.output.contains("--replicationCaCertFile"), result.output);
      assertFalse(configFile(result.serverRoot, "ads-truststore").exists(), "a trust store was left behind");
      assertFalse(configFile(result.serverRoot, "ads-truststore.pin").exists(), "a PIN file was left behind");
      assertEquals(cryptoManagerCertNicknames(result.serverRoot), List.of("ads-certificate"),
          "the configuration was written before the refusal");
      assertFalse(configFile(result.serverRoot, "truststore").exists(),
          "the certificates were configured before the refusal");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * A BCFKS key store is provisioned like the others, and the nickname has to match its
   * alias exactly: a BCFKS key store looks aliases up exactly where JKS, JCEKS and PKCS#12
   * fold them to lower case, so a nickname differing in case names a key pair the
   * installer cannot read, and is refused when the arguments are checked rather than
   * reported as a missing key pair half way through.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testSetupProvisionsABcfksKeyStoreUnderItsExactAlias() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreBcfks");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File keyStore = new File(workspace, "server.bcfks");
      final Provider bcFips = new BouncyCastleFipsProvider();
      final boolean registered = Security.addProvider(bcFips) != -1;
      try
      {
        ca.addKeyEntry(keyStore, "BCFKS", KEY_STORE_PASSWORD, "Server-Cert", "CN=host.example.com", true);
      }
      finally
      {
        if (registered)
        {
          Security.removeProvider(bcFips.getName());
        }
      }

      final SetupResult refused = runSetup(workspace, "--useBcfksKeystore", keyStore, "-O",
          "--certNickname", CERT_NICKNAME, "--useKeyStoreForReplication");
      assertNotEquals(refused.exitCode, 0, "setup accepted a nickname the BCFKS key store does not hold:\n"
          + refused.output);
      assertTrue(refused.output.contains("Server-Cert"), "the aliases of the key store are not listed:\n"
          + refused.output);
      assertFalse(configFile(refused.serverRoot, "config.ldif").exists(), "the refusal came after the arguments");
      TestCaseUtils.deleteDirectory(refused.serverRoot);

      final File serverRoot = installServer(workspace, "--useBcfksKeystore", keyStore, "-O",
          "--certNickname", "Server-Cert", "--useKeyStoreForReplication");
      final KeyStore keys = loadAdsTrustStore(serverRoot);
      assertTrue(keys.isKeyEntry("Server-Cert"), "the key pair to present was not imported");
      assertEquals(keys.getCertificateAlias(ca.getCaCertificate()), "ads-ca-1",
          "the issuing certificate is not trusted");
      assertEquals(cryptoManagerCertNicknames(serverRoot), List.of("Server-Cert"));
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

      final File serverRoot = installServer(workspace, "--usePkcs12keyStore", keyStore, "-O",
          "--certNickname", CERT_NICKNAME);

      assertFalse(configFile(serverRoot, "ads-truststore").exists(),
          "the trust store is provisioned without being asked for");
      assertEquals(cryptoManagerCertNicknames(serverRoot), List.of("ads-certificate"));
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /**
   * The installed server starts on the provisioned trust store: the trust store backend
   * opens it with the PIN setup wrote, generates the instance key next to the imported
   * entries, and the crypto manager finds the nickname it is configured with. This is
   * the road from the provisioned store to the replication port, which no other case
   * walks.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testInstalledServerStartsOnTheProvisionedTrustStore() throws Exception
  {
    final File workspace = TestCaseUtils.createTemporaryDirectory("adsTrustStoreStart");
    try
    {
      final CertificateFixture ca = new CertificateFixture("CN=Example CA,O=Example");
      final File keyStore = new File(workspace, "server.p12");
      ca.addKeyEntry(keyStore, "PKCS12", KEY_STORE_PASSWORD, CERT_NICKNAME, "CN=host.example.com", true);

      final File serverRoot;
      final SetupResult started = runSetup(workspace, "--usePkcs12keyStore", keyStore,
          "--certNickname", CERT_NICKNAME, "--useKeyStoreForReplication");
      serverRoot = started.serverRoot;
      try
      {
        assertEquals(started.exitCode, 0, "setup failed to start the server:\n" + started.output);
      }
      finally
      {
        stopServer(serverRoot);
      }

      final File errorLog = new File(serverRoot, "logs" + File.separator + "errors");
      for (String line : Files.readAllLines(errorLog.toPath(), StandardCharsets.UTF_8))
      {
        assertFalse(line.contains("severity=ERROR") && line.contains("ads-truststore"),
            "the first start could not use the provisioned trust store: " + line);
        assertFalse(line.contains("severity=ERROR") && line.contains(CERT_NICKNAME),
            "the first start could not find the nickname: " + line);
      }

      final KeyStore keys = loadAdsTrustStore(serverRoot);
      assertTrue(keys.isKeyEntry(CERT_NICKNAME), "the imported key pair did not survive the first start");
      assertTrue(keys.isCertificateEntry("ads-ca-1"), "the trusted certificate did not survive the first start");
      assertTrue(keys.isKeyEntry("ads-certificate"), "the server did not generate its instance key");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(workspace);
    }
  }

  /** Loads the provisioned trust store with the PIN setup wrote for it. */
  private KeyStore loadAdsTrustStore(File serverRoot) throws Exception
  {
    final File trustStore = configFile(serverRoot, "ads-truststore");
    final File pinFile = configFile(serverRoot, "ads-truststore.pin");
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

  private File configFile(File serverRoot, String name)
  {
    return new File(serverRoot, "config" + File.separator + name);
  }

  /** Extracts the built package and runs setup on it, returning the server root. */
  private File installServer(File workspace, String keyStoreArgument, File keyStore, String... extraArgs)
      throws Exception
  {
    final SetupResult result = runSetup(workspace, keyStoreArgument, keyStore, extraArgs);
    assertEquals(result.exitCode, 0, "setup failed:\n" + result.output);
    return result.serverRoot;
  }

  /**
   * Extracts the built package and runs setup on it, whether it succeeds or not. The
   * server is started unless {@code -O} is among the extra arguments.
   */
  private SetupResult runSetup(File workspace, String keyStoreArgument, File keyStore, String... extraArgs)
      throws Exception
  {
    final File serverRoot = new File(workspace, "opendj");
    new ZipExtractor(TestUtilities.getInstallPackageFile()).extract(serverRoot);

    final int[] ports = TestCaseUtils.findFreePorts(3);
    final List<String> args = new ArrayList<>();
    args.add(new File(serverRoot, OperatingSystem.isWindows() ? "setup.bat" : "setup").getPath());
    args.add("--cli");
    args.add("-n");
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
    args.add(keyStoreArgument);
    args.add(keyStore.getAbsolutePath());
    args.add("--keyStorePassword");
    args.add(KEY_STORE_PASSWORD);
    args.add("--enableStartTLS");
    args.addAll(List.of(extraArgs));

    // The output goes to a file rather than a pipe, so that a hung setup is killed on
    // expiry instead of holding the suite until the failsafe timeout.
    final File output = new File(workspace, "setup.out");
    final Process process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output).start();
    if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES))
    {
      process.destroyForcibly();
      fail("setup did not finish within " + TIMEOUT_MINUTES + " minutes:\n" + readOutput(output));
    }
    return new SetupResult(serverRoot, process.exitValue(), readOutput(output));
  }

  private String readOutput(File output) throws Exception
  {
    return new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
  }

  /** Stops the server setup started, if it is running. */
  private void stopServer(File serverRoot) throws Exception
  {
    final Installation installation = new Installation(serverRoot, serverRoot);
    if (installation.getStatus().isServerRunning())
    {
      new ServerController(installation).stopServer();
    }
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
    final File configFile = configFile(serverRoot, "config.ldif");
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
