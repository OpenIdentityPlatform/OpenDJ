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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.config.meta.FileBasedTrustManagerProviderCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.forgerock.opendj.util.StaticUtils.isFips;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.opends.server.extensions.FileBasedKeyManagerProviderTestCase.replace;
import static org.opends.server.extensions.FileBasedKeyManagerProviderTestCase.withPassword;
import static org.opends.server.util.ServerConstants.*;

/**
 * A set of test cases for the file-based trust manager provider.
 */
public class FileBasedTrustManagerProviderTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    FileWriter writer = new FileWriter(DirectoryServer.getInstanceRoot() +
                                       File.separator + "config" +
                                       File.separator + "server.pin");
    writer.write("password" + EOL);
    writer.close();

    writer = new FileWriter(DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "empty");
    writer.close();

    System.setProperty("org.opends.server.trustStorePIN", "password");
  }



  /**
   * Retrieves a set of valid configurations that can be used to
   * initialize the file-based trust manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/server.pin",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-property: org.opends.server.trustStorePIN",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "ds-cfg-trust-store-type: JKS");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an valid configurations.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testVvalidConfigs(Entry e)
         throws Exception
  {
    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(e);
    provider.finalizeTrustManagerProvider();
  }



  /**
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize the file-based trust manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/nosuchfile",
         "ds-cfg-trust-store-pin: password",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/nosuchfile",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-file: config/empty",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-property: nosuchproperty",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin-environment-variable: nosuchenv",
         "",
         "dn: cn=Trust Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-trust-manager-provider",
         "objectClass: ds-cfg-file-based-trust-manager-provider",
         "cn: Trust Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedTrustManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-trust-store-file: config/server.truststore",
         "ds-cfg-trust-store-pin: password",
         "ds-cfg-trust-store-type: invalid");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an invalid configuration.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigs(Entry e)
         throws Exception
  {
    initializeTrustManagerProvider(e);
    for (StringBuilder sb : e.toLDIF())
    {
      System.err.println(sb.toString());
    }
  }

  /**
   * A trust manager handed out by the provider checks certificates against what the trust
   * store file holds at the time of the check: a file replaced with other certificates, or with
   * the same certificates under another PIN, is loaded again, and a file that cannot be loaded
   * leaves the last certificates loaded in use.
   */
  @Test
  public void testTrustStoreLoadedAgainWhenChanged() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "reload-test.truststore");
    final File pinFile = new File(configDir, "reload-test.truststore.pin");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    replace(pinFile, ("password" + EOL).getBytes(StandardCharsets.UTF_8));

    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(TestCaseUtils.makeEntry(
        "dn: cn=Reloaded Trust Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-trust-manager-provider",
        "objectClass: ds-cfg-file-based-trust-manager-provider",
        "cn: Reloaded Trust Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedTrustManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-trust-store-file: config/reload-test.truststore",
        "ds-cfg-trust-store-pin-file: config/reload-test.truststore.pin"));
    try
    {
      final X509TrustManager trustManager = (X509TrustManager) provider.getTrustManagers()[0];
      // a plain trust manager stays plain, for JSSE adds checks of its own around it; outside
      // FIPS mode the provider wraps what it loads in a plain ExpirationCheckTrustManager
      assertThat(trustManager instanceof X509ExtendedTrustManager).isEqualTo(isFips());
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      // the PIN changes with the trust store, but the new PIN is not there yet
      replace(trustStore, withPassword(new File(configDir, "client.truststore"), "password", "changed"));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
      replace(pinFile, ("changed" + EOL).getBytes(StandardCharsets.UTF_8));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(2);

      replace(trustStore, "not a trust store".getBytes(StandardCharsets.UTF_8));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(2);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      Files.deleteIfExists(trustStore.toPath());
      Files.deleteIfExists(pinFile.toPath());
    }
  }

  /**
   * A trust store file that cannot be loaded is not tried again until it changes again, even
   * where it would load by then: the PIN below comes from a system property, which is not stamped.
   */
  @Test
  public void testTrustStoreNotLoadedAgainUntilChanged() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "retry-test.truststore");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    System.setProperty("retry.test.trust.store.pin", "password");
    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(TestCaseUtils.makeEntry(
        "dn: cn=Retried Trust Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-trust-manager-provider",
        "objectClass: ds-cfg-file-based-trust-manager-provider",
        "cn: Retried Trust Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedTrustManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-trust-store-file: config/retry-test.truststore",
        "ds-cfg-trust-store-pin-property: retry.test.trust.store.pin"));
    try
    {
      final X509TrustManager trustManager = (X509TrustManager) provider.getTrustManagers()[0];
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      replace(trustStore, withPassword(new File(configDir, "client.truststore"), "password", "changed"));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
      System.setProperty("retry.test.trust.store.pin", "changed");
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      System.clearProperty("retry.test.trust.store.pin");
      Files.deleteIfExists(trustStore.toPath());
    }
  }

  /**
   * A trust store renewed together with its PIN file is loaded with the new PIN by the provider
   * itself, as a component rebuilding its SSL context asks it to, with no certificate checked in
   * between to read the PIN again.
   */
  @Test
  public void testTrustStoreAndPinRenewedTogetherLoadedWithoutCheck() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "renewed-test.truststore");
    final File pinFile = new File(configDir, "renewed-test.truststore.pin");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    replace(pinFile, ("password" + EOL).getBytes(StandardCharsets.UTF_8));
    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(TestCaseUtils.makeEntry(
        "dn: cn=Renewed Trust Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-trust-manager-provider",
        "objectClass: ds-cfg-file-based-trust-manager-provider",
        "cn: Renewed Trust Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedTrustManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-trust-store-file: config/renewed-test.truststore",
        "ds-cfg-trust-store-pin-file: config/renewed-test.truststore.pin"));
    try
    {
      assertThat(((X509TrustManager) provider.getTrustManagers()[0]).getAcceptedIssuers()).hasSize(3);

      replace(trustStore, withPassword(new File(configDir, "client.truststore"), "password", "changed"));
      replace(pinFile, ("changed" + EOL).getBytes(StandardCharsets.UTF_8));
      assertThat(((X509TrustManager) provider.getTrustManagers()[0]).getAcceptedIssuers()).hasSize(2);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      Files.deleteIfExists(trustStore.toPath());
      Files.deleteIfExists(pinFile.toPath());
    }
  }

  /**
   * A changed configuration makes the trust managers already handed out load the trust store
   * again on their next check, even where the file has not changed since they last looked at it.
   */
  @Test
  public void testTrustStoreLoadedAgainWhenPinChangesInConfiguration() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "reconfigured-test.truststore");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(
        providerEntry("Reconfigured", "reconfigured-test.truststore", "password"));
    try
    {
      final X509TrustManager trustManager = (X509TrustManager) provider.getTrustManagers()[0];
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      // a trust store under a PIN the configuration does not have yet: the failed load records its stamps
      replace(trustStore, withPassword(new File(configDir, "client.truststore"), "password", "changed"));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      provider.applyConfigurationChange(InitializationUtils.getConfiguration(
          FileBasedTrustManagerProviderCfgDefn.getInstance(),
          providerEntry("Reconfigured", "reconfigured-test.truststore", "changed")));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(2);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      Files.deleteIfExists(trustStore.toPath());
    }
  }

  /**
   * Each trust manager handed out loads the trust store on its own. Asking the provider again, as
   * a component does to check a configuration change, leaves the trust managers handed out before
   * alone: the one below keeps the trust store it last loaded, for its file has not changed since
   * it failed to load it.
   */
  @Test
  public void testTrustManagerHandedOutAgainLeavesEarlierOnesAlone() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "handed-out-test.truststore");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    System.setProperty("handed.out.test.trust.store.pin", "password");
    FileBasedTrustManagerProvider provider = initializeTrustManagerProvider(TestCaseUtils.makeEntry(
        "dn: cn=Handed Out Trust Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-trust-manager-provider",
        "objectClass: ds-cfg-file-based-trust-manager-provider",
        "cn: Handed Out Trust Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedTrustManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-trust-store-file: config/handed-out-test.truststore",
        "ds-cfg-trust-store-pin-property: handed.out.test.trust.store.pin"));
    try
    {
      final X509TrustManager trustManager = (X509TrustManager) provider.getTrustManagers()[0];
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      replace(trustStore, withPassword(new File(configDir, "client.truststore"), "password", "changed"));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
      System.setProperty("handed.out.test.trust.store.pin", "changed");

      assertThat(((X509TrustManager) provider.getTrustManagers()[0]).getAcceptedIssuers()).hasSize(2);
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      System.clearProperty("handed.out.test.trust.store.pin");
      Files.deleteIfExists(trustStore.toPath());
    }
  }

  /**
   * A plain trust manager handed out outside FIPS mode loads the trust store as it was loaded
   * then, within an expiration check, after the server has turned to FIPS mode, and after it has
   * turned away from it again: a renewal is taken either way.
   */
  @Test
  public void testPlainTrustManagerLoadedAgainAsHandedOutWhenFipsModeChanges() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "fips-on-test.truststore");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    final FipsSwitchedTrustManagerProvider provider = InitializationUtils.initializeTrustManagerProvider(
        new FipsSwitchedTrustManagerProvider(), providerEntry("FIPS On", "fips-on-test.truststore", "password"),
        FileBasedTrustManagerProviderCfgDefn.getInstance());
    try
    {
      final X509TrustManager trustManager = (X509TrustManager) provider.getTrustManagers()[0];
      assertThat(trustManager).isNotInstanceOf(X509ExtendedTrustManager.class);
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      provider.fips = true;
      replace(trustStore, Files.readAllBytes(new File(configDir, "client.truststore").toPath()));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(2);

      provider.fips = false;
      replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      Files.deleteIfExists(trustStore.toPath());
    }
  }

  /**
   * In FIPS mode the provider hands out an extended trust manager, over the extended trust
   * manager it loads, and loads the trust store again the same way, even after the server has
   * turned away from FIPS mode, as it does once it has generated a certificate outside FIPS mode.
   */
  @Test
  public void testTrustManagerHandedOutInFipsModeIsExtended() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "fips-test.truststore");
    replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
    final FipsSwitchedTrustManagerProvider provider = new FipsSwitchedTrustManagerProvider();
    provider.fips = true;
    InitializationUtils.initializeTrustManagerProvider(provider,
        providerEntry("FIPS", "fips-test.truststore", "password"), FileBasedTrustManagerProviderCfgDefn.getInstance());
    try
    {
      final X509TrustManager trustManager = (X509TrustManager) provider.getTrustManagers()[0];
      assertThat(trustManager).isInstanceOf(X509ExtendedTrustManager.class);
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);

      replace(trustStore, Files.readAllBytes(new File(configDir, "client.truststore").toPath()));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(2);

      provider.fips = false;
      replace(trustStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
      assertThat(trustManager.getAcceptedIssuers()).hasSize(3);
    }
    finally
    {
      provider.finalizeTrustManagerProvider();
      Files.deleteIfExists(trustStore.toPath());
    }
  }

  /**
   * Each certificate check of a trust manager handed out, of either kind, loads a renewed trust
   * store first: the certificate a renewal drops is refused by the first check after it, and
   * taken again by the first check after the next renewal brings it back. A check given a socket
   * or an engine hands it on, and the one it delegates to refuses a check outside a handshake.
   */
  @Test
  public void testEveryCertificateCheckLoadsAgain() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File trustStore = new File(configDir, "check-test.truststore");
    final byte[] serverTrustStore = Files.readAllBytes(new File(configDir, "server.truststore").toPath());
    final byte[] clientTrustStore = Files.readAllBytes(new File(configDir, "client.truststore").toPath());
    // client.truststore does not hold this self-signed certificate
    final X509Certificate[] dropped =
        { trustedCertificate(new File(configDir, "server.truststore"), "client-emailaddress-cert") };
    replace(trustStore, serverTrustStore);
    final FipsSwitchedTrustManagerProvider plainProvider = InitializationUtils.initializeTrustManagerProvider(
        new FipsSwitchedTrustManagerProvider(), providerEntry("Plain Check", "check-test.truststore", "password"),
        FileBasedTrustManagerProviderCfgDefn.getInstance());
    final FipsSwitchedTrustManagerProvider extendedProvider = new FipsSwitchedTrustManagerProvider();
    extendedProvider.fips = true;
    InitializationUtils.initializeTrustManagerProvider(extendedProvider,
        providerEntry("Extended Check", "check-test.truststore", "password"),
        FileBasedTrustManagerProviderCfgDefn.getInstance());
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Socket socket = SSLSocketFactory.getDefault().createSocket(
            InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()))
    {
      final X509TrustManager plain = (X509TrustManager) plainProvider.getTrustManagers()[0];
      assertThat(plain).isNotInstanceOf(X509ExtendedTrustManager.class);
      final X509ExtendedTrustManager extended = (X509ExtendedTrustManager) extendedProvider.getTrustManagers()[0];
      final List<CertificateCheck> checks = Arrays.asList(
          chain -> plain.checkClientTrusted(chain, "RSA"),
          chain -> plain.checkServerTrusted(chain, "RSA"),
          chain -> extended.checkClientTrusted(chain, "RSA"),
          chain -> extended.checkServerTrusted(chain, "RSA"),
          chain -> extended.checkClientTrusted(chain, "RSA", (Socket) null),
          chain -> extended.checkServerTrusted(chain, "RSA", (Socket) null),
          chain -> extended.checkClientTrusted(chain, "RSA", (SSLEngine) null),
          chain -> extended.checkServerTrusted(chain, "RSA", (SSLEngine) null));
      for (int i = 0; i < checks.size(); i++)
      {
        replace(trustStore, clientTrustStore);
        assertRefused(checks.get(i), dropped, "check " + i + " after the renewal that drops the certificate");
        replace(trustStore, serverTrustStore);
        checks.get(i).check(dropped);
      }

      // a socket connected, or an engine, with no handshake under way
      final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
      assertRefused(chain -> extended.checkClientTrusted(chain, "RSA", socket), dropped, "client check, socket");
      assertRefused(chain -> extended.checkServerTrusted(chain, "RSA", socket), dropped, "server check, socket");
      assertRefused(chain -> extended.checkClientTrusted(chain, "RSA", engine), dropped, "client check, engine");
      assertRefused(chain -> extended.checkServerTrusted(chain, "RSA", engine), dropped, "server check, engine");
    }
    finally
    {
      plainProvider.finalizeTrustManagerProvider();
      extendedProvider.finalizeTrustManagerProvider();
      Files.deleteIfExists(trustStore.toPath());
    }
  }

  /** A certificate check of a trust manager. */
  private interface CertificateCheck
  {
    void check(X509Certificate[] chain) throws CertificateException;
  }

  private static void assertRefused(CertificateCheck check, X509Certificate[] chain, String description)
  {
    try
    {
      check.check(chain);
      fail(description + ": the certificate is taken");
    }
    catch (CertificateException expected)
    {
      // refused, as expected
    }
  }

  private static X509Certificate trustedCertificate(File trustStore, String alias) throws Exception
  {
    final KeyStore keyStore = KeyStore.getInstance("JKS");
    try (InputStream in = new FileInputStream(trustStore))
    {
      keyStore.load(in, "password".toCharArray());
    }
    return (X509Certificate) keyStore.getCertificate(alias);
  }

  /**
   * A provider whose FIPS mode the test turns on, instead of inserting a FIPS security provider
   * into the JVM, where it would stay for every test class run after this one.
   */
  private static final class FipsSwitchedTrustManagerProvider extends FileBasedTrustManagerProvider
  {
    private volatile boolean fips;

    @Override
    boolean isFipsMode()
    {
      return fips;
    }
  }

  private static Entry providerEntry(String name, String trustStoreFile, String pin) throws Exception
  {
    return TestCaseUtils.makeEntry(
        "dn: cn=" + name + " Trust Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-trust-manager-provider",
        "objectClass: ds-cfg-file-based-trust-manager-provider",
        "cn: " + name + " Trust Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedTrustManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-trust-store-file: config/" + trustStoreFile,
        "ds-cfg-trust-store-pin: " + pin);
  }

  private FileBasedTrustManagerProvider initializeTrustManagerProvider(Entry e) throws Exception {
    return InitializationUtils.initializeTrustManagerProvider(
        new FileBasedTrustManagerProvider(), e, FileBasedTrustManagerProviderCfgDefn.getInstance());
  }
}
