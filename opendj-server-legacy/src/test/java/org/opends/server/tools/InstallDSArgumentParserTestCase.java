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
package org.opends.server.tools;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ArgumentException;

/**
 * Tests the arguments which let {@code setup} provision the trust store used for server
 * to server communication from a key store the operator already holds.
 */
public class InstallDSArgumentParserTestCase extends DirectoryServerTestCase
{
  /** A fragment of the message reporting that the key pair to present has to come from a key store. */
  private static final String KEY_STORE_REQUIRED = "requires an existing key store";
  /** A fragment of the message reporting a certificate file which cannot be read. */
  private static final String CERT_FILE_INVALID = "does not exist";
  /** A fragment of the message reporting an argument the parser does not know. */
  private static final String UNKNOWN_ARGUMENT = "is not allowed for use with this program";

  /**
   * The key pair presented on the replication port is imported from an existing key
   * store, so asking for a self-signed certificate to be generated cannot satisfy it.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testUseKeyStoreForReplicationRejectsAGeneratedCertificate() throws Exception
  {
    assertReportsUseKeyStoreForReplication(true,
        "--cli", "-n", "-w", "password", "-b", "dc=example,dc=com",
        "--generateSelfSignedCertificate", "--enableStartTLS", "--useKeyStoreForReplication");
  }

  /**
   * The private key of a PKCS#11 token cannot be exported, so it cannot be copied into
   * the trust store used for replication.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testUseKeyStoreForReplicationRejectsAPkcs11Token() throws Exception
  {
    assertReportsUseKeyStoreForReplication(true,
        "--cli", "-n", "-w", "password", "-b", "dc=example,dc=com",
        "--usePkcs11Keystore", "--keyStorePassword", "password", "--enableStartTLS",
        "--useKeyStoreForReplication");
  }

  /**
   * A key store the installer can read the key pair from is accepted.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testUseKeyStoreForReplicationAcceptsAKeyStore() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("useKeyStoreForReplication");
    try
    {
      final File keyStore = new File(tmpDir, "server.p12");
      assertTrue(keyStore.createNewFile());
      assertReportsUseKeyStoreForReplication(false,
          "--cli", "-n", "-w", "password", "-b", "dc=example,dc=com",
          "--usePkcs12keyStore", keyStore.getAbsolutePath(), "--keyStorePassword", "password",
          "--enableStartTLS", "--useKeyStoreForReplication");
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * Certificates to trust on the replication port are only meaningful together with the
   * key pair to present there: a server which trusts an authority but keeps presenting
   * its self-signed certificate is the half-configured state this provisioning exists to
   * avoid.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testReplicationCaCertFileRequiresUseKeyStoreForReplication() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("replicationCaCertFile");
    try
    {
      final File caFile = new File(tmpDir, "ca.crt");
      assertTrue(caFile.createNewFile());
      final String error = parseAndReturnError(
          "--cli", "-n", "-w", "password", "-b", "dc=example,dc=com",
          "--usePkcs12keyStore", new File(tmpDir, "server.p12").getAbsolutePath(),
          "--keyStorePassword", "password", "--enableStartTLS",
          "--replicationCaCertFile", caFile.getAbsolutePath());
      assertTrue(error.contains("replicationCaCertFile") && error.contains("useKeyStoreForReplication"), error);
      assertFalse(error.contains(UNKNOWN_ARGUMENT), error);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * A certificate file which does not exist is reported while the arguments are checked,
   * rather than half way through the installation.
   *
   * @throws Exception
   *           If a problem occurs.
   */
  @Test
  public void testReplicationCaCertFileMustExist() throws Exception
  {
    final File tmpDir = TestCaseUtils.createTemporaryDirectory("replicationCaCertMissing");
    try
    {
      final File missing = new File(tmpDir, "nonexistent.crt");
      final String error = parseAndReturnError(
          "--cli", "-n", "-w", "password", "-b", "dc=example,dc=com",
          "--usePkcs12keyStore", new File(tmpDir, "server.p12").getAbsolutePath(),
          "--keyStorePassword", "password", "--enableStartTLS", "--useKeyStoreForReplication",
          "--replicationCaCertFile", missing.getAbsolutePath());
      assertTrue(error.contains(missing.getAbsolutePath()) && error.contains(CERT_FILE_INVALID), error);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tmpDir);
    }
  }

  /**
   * Asserts whether the arguments are rejected because {@code --useKeyStoreForReplication}
   * cannot be satisfied. Other errors, such as a port which happens to be in use on the
   * machine running the tests, are ignored: only the message under test is looked for. The
   * wording is matched as well as the argument name, so that an argument the parser does
   * not know at all, which is reported by name too, does not pass for the check under test.
   */
  private void assertReportsUseKeyStoreForReplication(boolean expected, String... args) throws Exception
  {
    final String error = parseAndReturnError(args);
    final boolean reported =
        error.contains("useKeyStoreForReplication") && error.contains(KEY_STORE_REQUIRED);
    if (expected)
    {
      assertTrue(reported, error);
    }
    else
    {
      assertFalse(reported, error);
      assertFalse(error.contains(UNKNOWN_ARGUMENT), error);
    }
  }

  /** Parses the provided arguments and returns the reported errors, empty if there is none. */
  private String parseAndReturnError(String... args) throws Exception
  {
    final InstallDSArgumentParser parser = new InstallDSArgumentParser(InstallDS.class.getName());
    parser.initializeArguments();
    try
    {
      parser.parseArguments(args);
      return "";
    }
    catch (ArgumentException e)
    {
      return e.getMessage();
    }
  }
}
