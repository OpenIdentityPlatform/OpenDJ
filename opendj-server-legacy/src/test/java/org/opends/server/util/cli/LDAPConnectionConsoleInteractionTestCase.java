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
package org.opends.server.util.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ToolMessages.INFO_CERTIFICATE_NAME_MISMATCH_TEXT_CLI;
import static org.opends.messages.ToolMessages.INFO_CERTIFICATE_NOT_TRUSTED_TEXT_CLI;
import static org.opends.server.TestCaseUtils.getTestResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.admin.client.cli.SecureConnectionCliArgs;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.FileBasedArgument;

/** Tests the console interaction when the certificate presented by a server is rejected. */
@SuppressWarnings("javadoc")
public class LDAPConnectionConsoleInteractionTestCase extends DirectoryServerTestCase
{
  private static final String HOST = "localhost";
  private static final int PORT = 4444;

  @Test
  public void testUntrustedCertificateIsReported()
  {
    assertThat(rejectionMessage(ApplicationTrustManager.Cause.NOT_TRUSTED))
        .isEqualTo(INFO_CERTIFICATE_NOT_TRUSTED_TEXT_CLI.get(HOST, PORT).toString());
  }

  @Test
  public void testHostNameMismatchIsReported()
  {
    assertThat(rejectionMessage(ApplicationTrustManager.Cause.HOST_NAME_MISMATCH))
        .isEqualTo(INFO_CERTIFICATE_NAME_MISMATCH_TEXT_CLI.get(HOST, PORT, HOST, HOST, PORT).toString());
  }

  /** The two causes must not lead to the same message: the user needs to know what is wrong. */
  @Test
  public void testTheTwoCausesAreReportedDifferently()
  {
    assertThat(rejectionMessage(ApplicationTrustManager.Cause.NOT_TRUSTED))
        .isNotEqualTo(rejectionMessage(ApplicationTrustManager.Cause.HOST_NAME_MISMATCH));
  }

  @DataProvider
  public Object[][] trustStorePasswords()
  {
    return new Object[][] {
        { "password\nignored\n", false, "" },
        { "stale\n", false, "password\n" },
        { null, false, "password\n" },
        { "stale\n", true, "" }
    };
  }

  @Test(dataProvider = "trustStorePasswords")
  public void testSavesCertificateToSuggestedTrustStore(String pinContents, boolean explicitPasswordFile,
      String passwordInput) throws Exception
  {
    final Path directory = Files.createTempDirectory("cli-trust-");
    final Path trustStore = directory.resolve("admin-truststore");
    final Path pin = directory.resolve("admin-truststore.pin");
    final Path passwordFile = directory.resolve("cli-password");
    final char[] password = "password".toCharArray();
    try
    {
      final KeyStore store = KeyStore.getInstance("JKS");
      store.load(null, password);
      try (OutputStream output = Files.newOutputStream(trustStore))
      {
        store.store(output, password);
      }
      if (pinContents != null)
      {
        Files.writeString(pin, pinContents);
      }

      final SecureConnectionCliArgs args = new SecureConnectionCliArgs(true);
      args.createGlobalArguments();
      args.getTrustStorePathArg().addValue(trustStore.toString());
      if (explicitPasswordFile)
      {
        Files.writeString(passwordFile, "password\n");
        args.getTrustStorePathArg().setPresent(true);
        final FileBasedArgument fileArg = args.getTrustStorePasswordFileArg();
        assertThat(fileArg.valueIsAcceptable(passwordFile.toString(), new LocalizableMessageBuilder()))
            .isTrue();
        fileArg.addValue(passwordFile.toString());
        fileArg.setPresent(true);
      }

      final X509Certificate certificate = loadServerCertificate();
      final String input = (explicitPasswordFile ? "" : "3\n") + "3\n\n" + passwordInput;
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final ConsoleApplication app = createConsole(input, output);
      assertThat(new LDAPConnectionConsoleInteraction(app, args)
          .checkServerCertificate(new X509Certificate[] { certificate }, "RSA", HOST)).isTrue();

      try (InputStream saved = Files.newInputStream(trustStore))
      {
        store.load(saved, password);
      }
      assertThat(store.getCertificate(certificate.getSubjectX500Principal().getName())).isEqualTo(certificate);
      assertThat(output.toString(StandardCharsets.UTF_8)).contains("[" + trustStore + "]");
    }
    finally
    {
      Files.deleteIfExists(passwordFile);
      Files.deleteIfExists(pin);
      Files.deleteIfExists(trustStore);
      Files.delete(directory);
    }
  }

  private static X509Certificate loadServerCertificate() throws Exception
  {
    final KeyStore fixture = KeyStore.getInstance("JKS");
    try (InputStream input = Files.newInputStream(getTestResource("server.keystore").toPath()))
    {
      fixture.load(input, "password".toCharArray());
    }
    return (X509Certificate) fixture.getCertificate("server-cert");
  }

  private static ConsoleApplication createConsole(String input, ByteArrayOutputStream output)
  {
    final InputStream stdin = System.in;
    final PrintStream printStream = new PrintStream(output, false, StandardCharsets.UTF_8);
    try
    {
      System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
      return new ConsoleApplication(printStream, printStream) { };
    }
    finally
    {
      System.setIn(stdin);
    }
  }

  private static String rejectionMessage(ApplicationTrustManager.Cause cause)
  {
    return LDAPConnectionConsoleInteraction.getCertificateRejectionMessage(cause, HOST, PORT).toString();
  }
}
