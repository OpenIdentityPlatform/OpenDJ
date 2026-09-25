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
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.X509ExtendedKeyManager;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.FileBasedKeyManagerProviderCfgDefn;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.util.ServerConstants.*;

/**
 * A set of test cases for the file-based key manager provider.
 */
public class FileBasedKeyManagerProviderTestCase
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

    System.setProperty("org.opends.server.KeyStorePIN", "password");
  }



  /**
   * Retrieves a set of valid configurations that can be used to
   * initialize the file-based key manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin: password",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-file: config/server.pin",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-property: org.opends.server.KeyStorePIN",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin: password",
         "ds-cfg-key-store-type: JKS",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server-cert.p12",
         "ds-cfg-key-store-pin: password",
         "ds-cfg-key-store-type: PKCS12",
         "",
         "dn: cn=No Key Store PIN,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore");


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
    FileBasedKeyManagerProvider provider = initializeKeyManagerProvider(e);
    provider.finalizeKeyManagerProvider();
  }



  /**
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize the file-based key manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=No Key Store File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-pin: password",
         "",
         "dn: cn=Nonexistent Key Store File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/nosuchfile",
         "ds-cfg-key-store-pin: password",
         "",
         "dn: cn=Nonexistent Key Store PIN File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-file: config/nosuchfile",
         "",
         "dn: cn=Empty Key Store PIN File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-file: config/empty",
         "",
         "dn: cn=Nonexistent Key Store PIN Property,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-property: nosuchproperty",
         "",
         "dn: cn=Nonexistent Key Store PIN Env Variable,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-environment-variable: nosuchenv",
         "",
         "dn: cn=Invalid Key Store Type,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin: password",
         "ds-cfg-key-store-type: invalid");


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
    initializeKeyManagerProvider(e);
  }

  /**
   * A key manager handed out by the provider presents what the key store file holds when a
   * handshake starts, not what it held when the key manager was handed out: a file replaced
   * with another certificate, or with the same certificate under another PIN, is loaded again.
   * A file that cannot be loaded, that holds no private key, or that holds none under the
   * aliases the file held keys under before leaves the last certificate loaded in use.
   */
  @Test
  public void testKeyStoreLoadedAgainWhenChanged() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File keyStore = new File(configDir, "reload-test.keystore");
    final File pinFile = new File(configDir, "reload-test.keystore.pin");
    replace(keyStore, Files.readAllBytes(new File(configDir, "server.keystore").toPath()));
    replace(pinFile, ("password" + EOL).getBytes(StandardCharsets.UTF_8));

    FileBasedKeyManagerProvider provider = initializeKeyManagerProvider(TestCaseUtils.makeEntry(
        "dn: cn=Reloaded Key Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-key-manager-provider",
        "objectClass: ds-cfg-file-based-key-manager-provider",
        "cn: Reloaded Key Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedKeyManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-key-store-file: config/reload-test.keystore",
        "ds-cfg-key-store-pin-file: config/reload-test.keystore.pin"));
    try
    {
      final X509ExtendedKeyManager keyManager = (X509ExtendedKeyManager) provider.getKeyManagers()[0];
      final String serverCertificate = serverCertificateOf(keyManager);

      // renewed under the alias the handler is configured with
      replace(keyStore, rewritten(new File(configDir, "client.keystore"), "password", "password", "server-cert"));
      final String clientCertificate = serverCertificateByAliasesOf(keyManager);
      assertThat(clientCertificate).isNotEqualTo(serverCertificate);

      // a handler presenting the key under its ssl-cert-nickname would find none in these
      replace(keyStore, rewritten(new File(configDir, "server.keystore"), "password", "password", "other-cert"));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(clientCertificate);
      replace(keyStore, Files.readAllBytes(new File(configDir, "server.truststore").toPath()));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(clientCertificate);

      // the PIN changes with the key store, but the new PIN is not there yet
      replace(keyStore, withPassword(new File(configDir, "server.keystore"), "password", "changed"));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(clientCertificate);
      replace(pinFile, ("changed" + EOL).getBytes(StandardCharsets.UTF_8));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(serverCertificate);

      replace(keyStore, "not a key store".getBytes(StandardCharsets.UTF_8));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(serverCertificate);
    }
    finally
    {
      provider.finalizeKeyManagerProvider();
      Files.deleteIfExists(keyStore.toPath());
      Files.deleteIfExists(pinFile.toPath());
    }
  }

  /**
   * A changed configuration makes the key managers already handed out load the key store again
   * on their next handshake, even where the file has not changed since they last looked at it.
   */
  @Test
  public void testKeyStoreLoadedAgainWhenPinChangesInConfiguration() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File keyStore = new File(configDir, "reconfigured-test.keystore");
    replace(keyStore, Files.readAllBytes(new File(configDir, "server.keystore").toPath()));
    FileBasedKeyManagerProvider provider = initializeKeyManagerProvider(reconfiguredProviderEntry("password"));
    try
    {
      final X509ExtendedKeyManager keyManager = (X509ExtendedKeyManager) provider.getKeyManagers()[0];
      final String serverCertificate = serverCertificateOf(keyManager);

      // a key store under a PIN the configuration does not have yet: the failed load records its stamps
      replace(keyStore, withPassword(new File(configDir, "client.keystore"), "password", "changed"));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(serverCertificate);

      // and whatever aliases the key store the new configuration names holds its keys under
      provider.applyConfigurationChange(InitializationUtils.getConfiguration(
          FileBasedKeyManagerProviderCfgDefn.getInstance(), reconfiguredProviderEntry("changed")));
      final String clientCertificate = serverCertificateOf(keyManager);
      assertThat(clientCertificate).isNotEqualTo(serverCertificate);

      // with no aliases to hold a store to, a store with no private key is still not taken
      provider.applyConfigurationChange(InitializationUtils.getConfiguration(
          FileBasedKeyManagerProviderCfgDefn.getInstance(), reconfiguredProviderEntry("changed")));
      replace(keyStore, withPassword(new File(configDir, "server.truststore"), "password", "changed"));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(clientCertificate);
    }
    finally
    {
      provider.finalizeKeyManagerProvider();
      Files.deleteIfExists(keyStore.toPath());
    }
  }

  private static Entry reconfiguredProviderEntry(String pin) throws Exception
  {
    return TestCaseUtils.makeEntry(
        "dn: cn=Reconfigured Key Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-key-manager-provider",
        "objectClass: ds-cfg-file-based-key-manager-provider",
        "cn: Reconfigured Key Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedKeyManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-key-store-file: config/reconfigured-test.keystore",
        "ds-cfg-key-store-pin: " + pin);
  }

  /**
   * A key store file that cannot be loaded is not tried again until it changes again, even where
   * it would load by then: the PIN below comes from a system property, which is not stamped.
   */
  @Test
  public void testKeyStoreNotLoadedAgainUntilChanged() throws Exception
  {
    final File configDir = new File(DirectoryServer.getInstanceRoot(), "config");
    final File keyStore = new File(configDir, "retry-test.keystore");
    replace(keyStore, Files.readAllBytes(new File(configDir, "server.keystore").toPath()));
    System.setProperty("retry.test.key.store.pin", "password");
    FileBasedKeyManagerProvider provider = initializeKeyManagerProvider(TestCaseUtils.makeEntry(
        "dn: cn=Retried Key Manager Provider,cn=SSL,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-key-manager-provider",
        "objectClass: ds-cfg-file-based-key-manager-provider",
        "cn: Retried Key Manager Provider",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedKeyManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-key-store-file: config/retry-test.keystore",
        "ds-cfg-key-store-pin-property: retry.test.key.store.pin"));
    try
    {
      final X509ExtendedKeyManager keyManager = (X509ExtendedKeyManager) provider.getKeyManagers()[0];
      final String serverCertificate = serverCertificateOf(keyManager);

      replace(keyStore, rewritten(new File(configDir, "client.keystore"), "password", "changed", "server-cert"));
      assertThat(serverCertificateOf(keyManager)).isEqualTo(serverCertificate);
      System.setProperty("retry.test.key.store.pin", "changed");
      assertThat(serverCertificateOf(keyManager)).isEqualTo(serverCertificate);
    }
    finally
    {
      provider.finalizeKeyManagerProvider();
      System.clearProperty("retry.test.key.store.pin");
      Files.deleteIfExists(keyStore.toPath());
    }
  }

  /** The road LDAPS takes with ssl-cert-nickname set: SelectableCertificateKeyManager asks getServerAliases. */
  private static String serverCertificateByAliasesOf(X509ExtendedKeyManager keyManager)
  {
    final String[] aliases = keyManager.getServerAliases("RSA", null);
    assertThat(aliases).isNotEmpty();
    return keyManager.getCertificateChain(aliases[0])[0].getSubjectX500Principal().getName();
  }

  private static String serverCertificateOf(X509ExtendedKeyManager keyManager)
  {
    final String alias = keyManager.chooseEngineServerAlias("RSA", null, null);
    assertThat(alias).isNotNull();
    return keyManager.getCertificateChain(alias)[0].getSubjectX500Principal().getName();
  }

  /** Returns the key store in the provided file, with its entries protected by another password. */
  static byte[] withPassword(File file, String oldPassword, String newPassword) throws Exception
  {
    return rewritten(file, oldPassword, newPassword, null);
  }

  /**
   * Returns the key store in the provided file, with its entries protected by another password
   * and, unless the alias provided is {@code null}, its private keys under that alias.
   */
  private static byte[] rewritten(File file, String oldPassword, String newPassword, String keyAlias)
      throws Exception
  {
    final KeyStore keyStore = KeyStore.getInstance("JKS");
    try (InputStream in = new FileInputStream(file))
    {
      keyStore.load(in, oldPassword.toCharArray());
    }
    for (String alias : Collections.list(keyStore.aliases()))
    {
      if (!keyStore.isKeyEntry(alias))
      {
        continue;
      }
      final Key key = keyStore.getKey(alias, oldPassword.toCharArray());
      final Certificate[] chain = keyStore.getCertificateChain(alias);
      keyStore.deleteEntry(alias);
      keyStore.setKeyEntry(keyAlias != null ? keyAlias : alias, key, newPassword.toCharArray(), chain);
    }
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    keyStore.store(out, newPassword.toCharArray());
    return out.toByteArray();
  }

  /** Replaces the file the way a renewal agent does: the new content is renamed over it. */
  static void replace(File file, byte[] content) throws Exception
  {
    final Path tmp = Files.createTempFile(file.getParentFile().toPath(), file.getName(), ".tmp");
    Files.write(tmp, content);
    Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private FileBasedKeyManagerProvider initializeKeyManagerProvider(Entry e) throws Exception {
    return InitializationUtils.initializeKeyManagerProvider(
        new FileBasedKeyManagerProvider(), e, FileBasedKeyManagerProviderCfgDefn.getInstance());
  }
}
