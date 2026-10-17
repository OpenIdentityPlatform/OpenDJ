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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.crypto.CryptoManagerImpl.CERT_NICKNAME_CHECK_INTERVAL_NANOS;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.types.Attributes.*;
import static org.opends.server.util.CollectionUtils.newArrayList;
import static org.testng.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.CryptoServicesRegistrar;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.RelaxRulesControl;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.server.config.server.CryptoManagerCfg;
import org.forgerock.util.Options;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.BlindTrustManager;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.TrustStoreBackend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Control;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.CryptoManagerException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.KeyStoreReplaceFailure;
import org.opends.server.util.Platform.KeyType;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 This class tests the CryptoManager.
 */
@SuppressWarnings("javadoc")
public class CryptoManagerTestCase extends CryptoTestCase {

  @BeforeClass
  public void setUp()
         throws Exception {
    TestCaseUtils.startServer();
  }

  @AfterClass
  public void CleanUp() throws Exception {
    // Removes at least secret keys added in this test case.
    TestCaseUtils.restartServer();
  }

  /**
   The nickname check must answer for the trust store the SSL context is built from: the
   instance key pair the server generated for itself is held under its alias, a nickname
   which was never imported is not.
   */
  @Test
  public void testTrustStoreKnowsWhichCertNicknamesItHolds() throws Exception
  {
    // Generates the ads-certificate key pair if the trust store does not hold it yet.
    assertNotNull(CryptoManagerImpl.getInstanceKeyCertificateFromLocalTruststore());
    final TrustStoreBackend trustStore = (TrustStoreBackend) getServerContext()
        .getBackendConfigManager().getLocalBackendById(ID_ADS_TRUST_STORE_BACKEND);

    assertThat(trustStore.containsKeyWithAlias(ADS_CERTIFICATE_ALIAS)).isTrue();
    assertThat(trustStore.containsKeyWithAlias("no-such-nickname")).isFalse();
  }

  /**
   A server which cannot present its certificate reconnects every 500 ms and a new SSL
   context is built for every attempt, so a certificate nickname missing from the trust
   store is looked up, and reported, at most once per interval and per component -- but
   again on the next interval, for as long as it is missing.
   */
  @Test
  public void testMissingCertNicknameIsReportedOncePerInterval()
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final String checked = "Replication Server:" + UUID.randomUUID();
    final long start = System.nanoTime();

    assertThat(cm.isCertNicknameCheckDue(checked, start))
        .as("the nickname has never been looked up").isTrue();
    assertThat(cm.isCertNicknameCheckDue(checked, start + 1))
        .as("the next connection attempt does not look it up again").isFalse();
    assertThat(cm.isCertNicknameCheckDue(checked, start + CERT_NICKNAME_CHECK_INTERVAL_NANOS - 1))
        .as("nor does the last attempt of the interval").isFalse();
    assertThat(cm.isCertNicknameCheckDue(checked, start + CERT_NICKNAME_CHECK_INTERVAL_NANOS))
        .as("a whole interval later it is looked up again").isTrue();
    assertThat(cm.isCertNicknameCheckDue(checked, start + CERT_NICKNAME_CHECK_INTERVAL_NANOS + 1))
        .as("and the next interval starts from that look up").isFalse();
  }

  /**
   The crypto manager reads its SSL properties when it is created and replication keeps
   what it read, so a change to ssl-cert-nickname is accepted into the configuration
   without being in force. The change listener says so rather than staying silent.
   */
  @Test
  public void testSslCertNicknameChangeReportsThatARestartIsRequired() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();

    final ConfigChangeResult ccr = cm.applyConfigurationChange(withSslCertNicknames(cfg, "no-such-nickname"));

    assertThat(ccr.getResultCode()).as("the change is accepted").isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).as("but it is not in force").isTrue();
    assertThat(ccr.getMessages())
        .as("and the configuration change is not applied silently")
        .contains(WARN_CRYPTOMGR_SSL_PROPERTY_REQUIRES_RESTART.get("ssl-cert-nickname"));
  }

  @DataProvider
  public Object[][] sslPropertiesWhichNeedARestart()
  {
    // Any value the one in force does not equal: the server starts with no protocol and no
    // cipher suite configured, and with ds-cfg-ssl-encryption: false.
    return new Object[][] {
      { "getSSLProtocol", new TreeSet<>(Arrays.asList("TLSv1.3")), "ssl-protocol" },
      { "getSSLCipherSuite", new TreeSet<>(Arrays.asList("TLS_AES_256_GCM_SHA384")), "ssl-cipher-suite" },
      { "isSSLEncryption", true, "ssl-encryption" },
    };
  }

  /**
   The other SSL properties are read once as well, and used to say that no action was
   required. A change to any of them is reported the same way and, unlike a nickname, is
   looked up nowhere: the restart is the whole report.
   */
  @Test(dataProvider = "sslPropertiesWhichNeedARestart")
  public void testSslPropertyChangeReportsThatARestartIsRequired(
      final String getter, final Object value, final String property) throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();

    final ConfigChangeResult ccr = cm.applyConfigurationChange(withProperty(cfg, getter, value));

    assertThat(ccr.getResultCode()).isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).containsExactly(WARN_CRYPTOMGR_SSL_PROPERTY_REQUIRES_RESTART.get(property));
  }

  /**
   The report is about what changed: a change to another property of the crypto manager,
   or a re-read of the configuration as it stands, asks for no restart.
   */
  @Test
  public void testUnchangedSslPropertiesRequireNoRestart() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();

    final ConfigChangeResult ccr = cm.applyConfigurationChange(cfg);

    assertThat(ccr.adminActionRequired()).isFalse();
    assertThat(ccr.getMessages()).isEmpty();
  }

  /**
   The cryptographic properties are the ones the change listener does apply: a new digest
   algorithm is in use as soon as the change is made, and asks for nothing.
   */
  @Test
  public void testCryptoPropertyChangeIsApplied() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final String inForce = cm.getPreferredMessageDigestAlgorithm();
    final String changed = "SHA-512";
    assertThat(changed).as("the change is a change").isNotEqualTo(inForce);
    try
    {
      final ConfigChangeResult ccr = cm.applyConfigurationChange(withProperty(cfg, "getDigestAlgorithm", changed));

      assertThat(cm.getPreferredMessageDigestAlgorithm()).as("the new algorithm is in use").isEqualTo(changed);
      assertThat(ccr.adminActionRequired()).as("and no action is asked for").isFalse();
      assertThat(ccr.getMessages()).isEmpty();
    }
    finally
    {
      // The crypto manager is the one every test of the running server shares.
      cm.applyConfigurationChange(cfg);
    }
  }

  /**
   A nickname which the trust store does not hold presents no certificate to the
   replication peers, and until the server is restarted the mistake shows up nowhere. The
   nicknames are looked up when the change is made, and only the missing one is reported.
   */
  @Test
  public void testSslCertNicknameChangeReportsTheNicknamesTheTrustStoreDoesNotHold() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final TrustStoreBackend trustStore = (TrustStoreBackend) getServerContext()
        .getBackendConfigManager().getLocalBackendById(ID_ADS_TRUST_STORE_BACKEND);

    final ConfigChangeResult ccr =
        cm.applyConfigurationChange(withSslCertNicknames(cfg, ADS_CERTIFICATE_ALIAS, "no-such-nickname"));

    assertThat(ccr.getMessages())
        .as("the nickname which is not in the trust store is named")
        .contains(WARN_CRYPTOMGR_SSL_CERT_NICKNAME_NOT_IN_TRUST_STORE.get(
            "no-such-nickname", trustStore.getTrustStoreFile()))
        .as("while the one it holds is not")
        .doesNotContain(WARN_CRYPTOMGR_SSL_CERT_NICKNAME_NOT_IN_TRUST_STORE.get(
            ADS_CERTIFICATE_ALIAS, trustStore.getTrustStoreFile()));
  }

  /**
   The lookup is best effort: a trust store which cannot be read costs the administrator
   the report of the nicknames it does not hold, and the change is stored either way. That
   cost is named next to the restart the change asks for, rather than paid silently.
   */
  @Test
  public void testSslCertNicknameChangeReportsATrustStoreItCannotRead() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final TrustStoreBackend trustStore = (TrustStoreBackend) getServerContext()
        .getBackendConfigManager().getLocalBackendById(ID_ADS_TRUST_STORE_BACKEND);
    final Path trustStoreFile = StaticUtils.getFileForPath(trustStore.getTrustStoreFile()).toPath();
    final Path movedAway = trustStoreFile.resolveSibling(trustStoreFile.getFileName() + ".moved-away");

    // The trust store is opened on every lookup, so it cannot be read for exactly as long
    // as the file is out of the way.
    Files.move(trustStoreFile, movedAway);
    final ConfigChangeResult ccr;
    try
    {
      ccr = cm.applyConfigurationChange(withSslCertNicknames(cfg, "no-such-nickname"));
    }
    finally
    {
      Files.move(movedAway, trustStoreFile);
    }

    assertThat(ccr.getResultCode()).as("the change is stored either way").isEqualTo(ResultCode.SUCCESS);
    assertThat(ccr.adminActionRequired()).isTrue();
    assertThat(ccr.getMessages()).extracting(LocalizableMessage::ordinal)
        .as("the restart is asked for, and the lookup which could not be done is named in place of its result")
        .containsExactly(WARN_CRYPTOMGR_SSL_PROPERTY_REQUIRES_RESTART.ordinal(),
            WARN_CRYPTOMGR_SSL_CERT_NICKNAME_LOOKUP_FAILED.ordinal());
    assertThat(ccr.getMessages().get(1).toString())
        .as("with the trust store which could not be read")
        .contains(trustStore.getTrustStoreFile());
  }

  /**
   Replication replays the instance keys which reach cn=admin data together on parallel threads,
   and the trust store file is rewritten for each of them: none of the certificates may be lost,
   and the SSL contexts, which are built from that file, must never find it half written. A
   directory server which loses the certificate of a replication server that way fails every
   handshake with it (#1188).
   */
  @Test
  public void testInstanceKeysAddedTogetherAllReachTheTrustStore() throws Exception
  {
    addInstanceKeysTogetherWhileReadingTheTrustStore();
  }

  /**
   The same, with a trust store file which cannot be replaced at once and is written in place -
   one mounted on its own into a container, in a directory this process cannot write to, or one
   on Windows: there only the lock of the trust store keeps the SSL contexts
   from finding it half written.
   */
  @Test
  public void testInstanceKeysAddedTogetherInPlaceAllReachTheTrustStore() throws Exception
  {
    try (AutoCloseable inPlace = KeyStoreReplaceFailure.failKeyStoreReplace())
    {
      addInstanceKeysTogetherWhileReadingTheTrustStore();
    }
  }

  private void addInstanceKeysTogetherWhileReadingTheTrustStore() throws Exception
  {
    final int keys = 16;
    final TrustStoreBackend trustStore = (TrustStoreBackend) getServerContext()
        .getBackendConfigManager().getLocalBackendById(ID_ADS_TRUST_STORE_BACKEND);
    final Map<DN, ByteString> certificates = generateInstanceKeyCertificates(keys);
    final ExecutorService executor = Executors.newFixedThreadPool(keys + 1);
    final CyclicBarrier start = new CyclicBarrier(keys + 1);
    final AtomicBoolean adding = new AtomicBoolean(true);
    try
    {
      final List<Future<ResultCode>> adds = new ArrayList<>();
      for (Map.Entry<DN, ByteString> certificate : certificates.entrySet())
      {
        final Entry instanceKey = instanceKeyEntry(certificate.getKey(), certificate.getValue());
        adds.add(executor.submit(() -> {
          start.await();
          return getRootConnection().processAdd(instanceKey).getResultCode();
        }));
      }
      final Future<String> reads = executor.submit(() -> {
        start.await();
        do
        {
          try
          {
            trustStore.getTrustManagers();
          }
          catch (DirectoryException e)
          {
            return e.getMessage();
          }
        }
        while (adding.get());
        return null;
      });

      for (Future<ResultCode> add : adds)
      {
        assertThat(add.get(60, TimeUnit.SECONDS)).isEqualTo(ResultCode.SUCCESS);
      }
      adding.set(false);
      assertThat(reads.get(60, TimeUnit.SECONDS)).as("a trust store read while the keys were added").isNull();

      assertThat(trustedInstanceKeys(trustStore)).as("the instance keys the trust store holds")
          .containsAll(instanceKeyIDs(certificates));
    }
    finally
    {
      adding.set(false);
      executor.shutdownNow();
      // an add which outlived its wait above would put its entries back after the deletes
      executor.awaitTermination(60, TimeUnit.SECONDS);
      for (DN instanceKey : certificates.keySet())
      {
        getRootConnection().processDelete(instanceKey);
      }
    }
  }

  /**
   Replication replays the deletes of instance keys together with the adds of others: a delete
   lost to an add would leave a compromised key trusted, an add lost to a delete would leave a
   server untrusted (#1188).
   */
  @Test
  public void testInstanceKeysAddedAndDeletedTogetherAllReachTheTrustStore() throws Exception
  {
    final int keys = 16;
    final TrustStoreBackend trustStore = (TrustStoreBackend) getServerContext()
        .getBackendConfigManager().getLocalBackendById(ID_ADS_TRUST_STORE_BACKEND);
    final Map<DN, ByteString> deleted = generateInstanceKeyCertificates(keys);
    final Map<DN, ByteString> added = generateInstanceKeyCertificates(keys);
    final ExecutorService executor = Executors.newFixedThreadPool(2 * keys);
    final CyclicBarrier start = new CyclicBarrier(2 * keys);
    try
    {
      for (Map.Entry<DN, ByteString> certificate : deleted.entrySet())
      {
        assertThat(getRootConnection().processAdd(instanceKeyEntry(certificate.getKey(), certificate.getValue()))
            .getResultCode()).isEqualTo(ResultCode.SUCCESS);
      }
      assertThat(trustedInstanceKeys(trustStore)).as("the instance keys the trust store holds before")
          .containsAll(instanceKeyIDs(deleted));

      final List<Future<ResultCode>> changes = new ArrayList<>();
      for (Map.Entry<DN, ByteString> certificate : added.entrySet())
      {
        final Entry instanceKey = instanceKeyEntry(certificate.getKey(), certificate.getValue());
        changes.add(executor.submit(() -> {
          start.await();
          return getRootConnection().processAdd(instanceKey).getResultCode();
        }));
      }
      for (DN instanceKey : deleted.keySet())
      {
        changes.add(executor.submit(() -> {
          start.await();
          return getRootConnection().processDelete(instanceKey).getResultCode();
        }));
      }
      for (Future<ResultCode> change : changes)
      {
        assertThat(change.get(60, TimeUnit.SECONDS)).isEqualTo(ResultCode.SUCCESS);
      }

      final Set<String> trusted = trustedInstanceKeys(trustStore);
      assertThat(trusted).as("the instance keys the trust store holds").containsAll(instanceKeyIDs(added));
      assertThat(trusted).as("the instance keys the trust store holds")
          .doesNotContainAnyElementsOf(instanceKeyIDs(deleted));
    }
    finally
    {
      executor.shutdownNow();
      // a change which outlived its wait above would put its entries back after the deletes
      executor.awaitTermination(60, TimeUnit.SECONDS);
      for (DN instanceKey : added.keySet())
      {
        getRootConnection().processDelete(instanceKey);
      }
      for (DN instanceKey : deleted.keySet())
      {
        getRootConnection().processDelete(instanceKey);
      }
    }
  }

  /** The IDs of the instance keys whose certificates the trust store holds. */
  private static Set<String> trustedInstanceKeys(TrustStoreBackend trustStore) throws Exception
  {
    final Set<String> trusted = new HashSet<>();
    for (TrustManager trustManager : trustStore.getTrustManagers())
    {
      for (X509Certificate issuer : ((X509TrustManager) trustManager).getAcceptedIssuers())
      {
        trusted.add(CryptoManagerImpl.getInstanceKeyID(issuer.getEncoded()));
      }
    }
    return trusted;
  }

  private static List<String> instanceKeyIDs(Map<DN, ByteString> certificates) throws Exception
  {
    final List<String> ids = new ArrayList<>();
    for (ByteString certificate : certificates.values())
    {
      ids.add(CryptoManagerImpl.getInstanceKeyID(certificate.toByteArray()));
    }
    return ids;
  }

  /**
   An instance key the trust store cannot take, such as one which reached cn=admin data without
   its certificate, leaves this server without trust in the server it belongs to, so every
   replication connection to that server fails its handshake: the refusal must be in the error
   log, which is where the administrator of such a server looks (#1188). A key without its
   certificate is refused by the schema check of its copy, before the trust store backend sees
   it; a refusal of the backend itself takes the same branch of CryptoManagerSync, and keeps its
   result code (testTrustStoreRefusesAnAliasByWhatIsWrongWithIt).
   */
  @Test
  public void testInstanceKeyTheTrustStoreRefusesIsReported() throws Exception
  {
    final DN instanceKey = generateInstanceKeyCertificates(1).keySet().iterator().next();
    final Entry withoutCertificate = new Entry(instanceKey, null, null, null);
    withoutCertificate.addObjectClass(CoreSchema.getTopObjectClass());
    withoutCertificate.addObjectClass(getServerContext().getSchema().getObjectClass(OC_CRYPTO_INSTANCE_KEY));
    withoutCertificate.addAttribute(
        create(ATTR_CRYPTO_KEY_ID, instanceKey.rdn().getFirstAVA().getAttributeValue().toString()));
    final DN trustStoreEntry = DN.valueOf(DN_TRUST_STORE_ROOT).child(instanceKey.rdn());

    TestCaseUtils.ERROR_TEXT_WRITER.clear();
    try
    {
      // Relaxed, as only a replayed add can store an instance key without its certificate.
      assertThat(getRootConnection().processAdd(withoutCertificate,
              newArrayList((Control) new LDAPControl(RelaxRulesControl.OID, false))).getResultCode())
          .isEqualTo(ResultCode.SUCCESS);

      assertThat(DirectoryServer.getEntry(trustStoreEntry)).as("the instance key in the trust store").isNull();
      assertThat(TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
          .as("the error log")
          .anyMatch(record -> record.contains("severity=ERROR") && record.contains(trustStoreEntry.toString()));
    }
    finally
    {
      getRootConnection().processDelete(instanceKey);
    }
  }

  /**
   dsreplication seeds the trust store of a server it enables with the instance keys of the
   topology, so the add of an instance key may find its certificate there already: that is no
   failure, and the error log must stay quiet about it.
   */
  @Test
  public void testInstanceKeyTheTrustStoreHoldsAlreadyIsNotReported() throws Exception
  {
    final Map.Entry<DN, ByteString> certificate =
        generateInstanceKeyCertificates(1).entrySet().iterator().next();
    final DN instanceKey = certificate.getKey();
    final DN trustStoreEntry = DN.valueOf(DN_TRUST_STORE_ROOT).child(instanceKey.rdn());
    try
    {
      assertThat(getRootConnection().processAdd(instanceKeyEntry(trustStoreEntry, certificate.getValue()))
          .getResultCode()).as("the seeding of the trust store").isEqualTo(ResultCode.SUCCESS);

      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      assertThat(getRootConnection().processAdd(instanceKeyEntry(instanceKey, certificate.getValue()))
          .getResultCode()).isEqualTo(ResultCode.SUCCESS);

      assertThat(DirectoryServer.getEntry(trustStoreEntry)).as("the instance key in the trust store").isNotNull();
      assertThat(TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
          .as("the error log")
          .noneMatch(record -> record.contains("severity=ERROR") && record.contains(trustStoreEntry.toString()));
    }
    finally
    {
      getRootConnection().processDelete(instanceKey);
      getRootConnection().processDelete(trustStoreEntry);
    }
  }

  /**
   An instance key marked compromised has to leave the trust store: while its certificate is
   there, this server goes on trusting whoever holds the key. A trust store which cannot drop it
   must say so in the error log, as it does for a certificate it cannot take.
   */
  @Test
  public void testCompromisedInstanceKeyTheTrustStoreCannotDropIsReported() throws Exception
  {
    final Map.Entry<DN, ByteString> certificate =
        generateInstanceKeyCertificates(1).entrySet().iterator().next();
    final DN instanceKey = certificate.getKey();
    final DN trustStoreEntry = DN.valueOf(DN_TRUST_STORE_ROOT).child(instanceKey.rdn());
    try
    {
      assertThat(getRootConnection().processAdd(instanceKeyEntry(instanceKey, certificate.getValue()))
          .getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(DirectoryServer.getEntry(trustStoreEntry)).as("the instance key in the trust store").isNotNull();

      ShortCircuitPlugin.registerShortCircuit(OperationType.DELETE, "PreOperation",
          ResultCode.UNWILLING_TO_PERFORM.intValue(), 0, 1,
          op -> trustStoreEntry.equals(((PreOperationDeleteOperation) op).getEntryDN()));
      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      assertThat(getRootConnection().processModify(instanceKey, newArrayList(new Modification(
              ADD, create(ATTR_CRYPTO_KEY_COMPROMISED_TIME, TimeThread.getGeneralizedTime()))))
          .getResultCode()).isEqualTo(ResultCode.SUCCESS);

      assertThat(DirectoryServer.getEntry(trustStoreEntry)).as("the compromised key in the trust store").isNotNull();
      assertThat(TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
          .as("the error log")
          .anyMatch(record -> record.contains("severity=ERROR") && record.contains(trustStoreEntry.toString()));
    }
    finally
    {
      ShortCircuitPlugin.deregisterShortCircuit(OperationType.DELETE, "PreOperation");
      getRootConnection().processDelete(instanceKey);
      getRootConnection().processDelete(trustStoreEntry);
    }
  }

  /**
   The delete of an instance key whose certificate has left the trust store already - when the
   key was marked compromised, say - finds nothing to drop there: that is no failure, and the
   error log must stay quiet about it.
   */
  @Test
  public void testInstanceKeyTheTrustStoreLacksAlreadyIsNotReported() throws Exception
  {
    final Map.Entry<DN, ByteString> certificate =
        generateInstanceKeyCertificates(1).entrySet().iterator().next();
    final DN instanceKey = certificate.getKey();
    final DN trustStoreEntry = DN.valueOf(DN_TRUST_STORE_ROOT).child(instanceKey.rdn());
    try
    {
      assertThat(getRootConnection().processAdd(instanceKeyEntry(instanceKey, certificate.getValue()))
          .getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThat(getRootConnection().processDelete(trustStoreEntry).getResultCode())
          .as("the certificate leaving the trust store first").isEqualTo(ResultCode.SUCCESS);

      TestCaseUtils.ERROR_TEXT_WRITER.clear();
      assertThat(getRootConnection().processDelete(instanceKey).getResultCode()).isEqualTo(ResultCode.SUCCESS);

      assertThat(TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
          .as("the error log")
          .noneMatch(record -> record.contains("severity=ERROR") && record.contains(trustStoreEntry.toString()));
    }
    finally
    {
      getRootConnection().processDelete(instanceKey);
      getRootConnection().processDelete(trustStoreEntry);
    }
  }

  /**
   The trust store refuses an alias it holds already as an entry which exists, and the delete of
   one it does not hold as an entry which does not, whichever check catches it: the sync of the
   instance keys tells those apart from a failure by their result codes, and reports a failure in
   the error log. Here the backend is called directly, past the existence check of the core, as an
   add which lost the race for its alias to another one would reach it.
   */
  @Test
  public void testTrustStoreRefusesAnAliasByWhatIsWrongWithIt() throws Exception
  {
    final TrustStoreBackend trustStore = (TrustStoreBackend) getServerContext()
        .getBackendConfigManager().getLocalBackendById(ID_ADS_TRUST_STORE_BACKEND);
    final Map.Entry<DN, ByteString> certificate =
        generateInstanceKeyCertificates(1).entrySet().iterator().next();
    final DN trustStoreEntry = DN.valueOf(DN_TRUST_STORE_ROOT).child(certificate.getKey().rdn());
    try
    {
      assertThat(getRootConnection().processAdd(instanceKeyEntry(trustStoreEntry, certificate.getValue()))
          .getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThatThrownBy(() -> trustStore.addEntry(instanceKeyEntry(trustStoreEntry, certificate.getValue()), null))
          .as("an alias in use")
          .isInstanceOfSatisfying(DirectoryException.class,
              e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.ENTRY_ALREADY_EXISTS));

      assertThat(getRootConnection().processDelete(trustStoreEntry).getResultCode()).isEqualTo(ResultCode.SUCCESS);
      assertThatThrownBy(() -> trustStore.deleteEntry(trustStoreEntry, null))
          .as("an alias not in use")
          .isInstanceOfSatisfying(DirectoryException.class,
              e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.NO_SUCH_OBJECT));
    }
    finally
    {
      getRootConnection().processDelete(trustStoreEntry);
    }
  }

  /** Generates self-signed certificates, by the DN of the instance key entry each would have. */
  private static Map<DN, ByteString> generateInstanceKeyCertificates(int count) throws Exception
  {
    final Path keyStore = Files.createTempFile("instance-keys", ".jks");
    Files.delete(keyStore);
    try
    {
      final CertificateManager certificateManager =
          new CertificateManager(keyStore.toString(), CertificateManager.KEY_STORE_TYPE_JKS, "password");
      final Map<DN, ByteString> certificates = new LinkedHashMap<>();
      for (int i = 0; i < count; i++)
      {
        final String alias = "instance-key-" + i;
        certificateManager.generateSelfSignedCertificate(KeyType.EC, alias, "CN=" + alias, 1);
        final byte[] certificate = certificateManager.getCertificate(alias).getEncoded();
        certificates.put(
            DN.valueOf(ATTR_CRYPTO_KEY_ID + "=" + CryptoManagerImpl.getInstanceKeyID(certificate)
                + ",cn=instance keys,cn=admin data"),
            ByteString.wrap(certificate));
      }
      return certificates;
    }
    finally
    {
      Files.deleteIfExists(keyStore);
    }
  }

  /** Builds an instance key entry the way the crypto manager publishes its own in cn=admin data. */
  private static Entry instanceKeyEntry(DN dn, ByteString certificate) throws DirectoryException
  {
    final Entry entry = new Entry(dn, null, null, null);
    entry.addObjectClass(CoreSchema.getTopObjectClass());
    entry.addObjectClass(getServerContext().getSchema().getObjectClass(OC_CRYPTO_INSTANCE_KEY));
    entry.addAttribute(create(ATTR_CRYPTO_KEY_ID, dn.rdn().getFirstAVA().getAttributeValue().toString()));
    final AttributeBuilder builder =
        new AttributeBuilder(getServerContext().getSchema().getAttributeType(ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE));
    builder.setOption("binary");
    builder.add(certificate);
    entry.addAttribute(builder.toAttribute());
    return entry;
  }

  /**
   A key wrapping transformation this Java runtime cannot provide is refused, at start (where
   the refusal is what keeps the server from starting) as on a change, and the refusal has to
   name the property to set, not only the cipher which failed: on a FIPS-restricted runtime
   without RSA-OAEP that is all the administrator has to go on.
   */
  @Test
  public void testUnsupportedKeyWrappingTransformationIsRefusedNamingTheProperty() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final String unsupported = "RSA/ECB/NoSuchPadding";
    final List<LocalizableMessage> why = new ArrayList<>();

    final boolean acceptable =
        cm.isConfigurationChangeAcceptable(withProperty(cfg, "getKeyWrappingTransformation", unsupported), why);

    assertThat(acceptable).isFalse();
    assertThat(why).hasSize(1);
    assertThat(why.get(0).ordinal()).isEqualTo(ERR_CRYPTOMGR_KEY_WRAPPING_TRANSFORMATION_UNSUPPORTED.ordinal());
    assertThat(why.get(0).toString()).contains(unsupported).contains("key-wrapping-transformation");
  }

  /**
   The check wraps with a key which a provider running in FIPS approved-only mode accepts: BC-FIPS
   in that mode refuses RSA keys under 2048 bits with an Error, and the check runs at every start,
   so a smaller key keeps the server from starting whatever the transformation.
   */
  @Test
  public void testKeyWrappingCheckPassesUnderApprovedOnlyBcFips() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final String supported = "RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING";
    assertThat(supported).isNotEqualTo(cfg.getKeyWrappingTransformation());
    final List<LocalizableMessage> why = new ArrayList<>();

    final boolean acceptable = inApprovedOnlyBcFipsThread(() -> cm.isConfigurationChangeAcceptable(
        withProperty(withMacKeyFromSunJce(cfg), "getKeyWrappingTransformation", supported), why));

    assertThat(why).isEmpty();
    assertThat(acceptable).isTrue();
  }

  /**
   A provider refusing the wrap with an Error, as BC-FIPS in approved-only mode refuses PKCS#1 v1.5
   encryption, is reported as a refusal of the configuration rather than escaping from the check.
   */
  @Test
  public void testKeyWrappingRefusedWithAnErrorIsReported() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final String unapproved = "RSA/ECB/PKCS1Padding";
    final List<LocalizableMessage> why = new ArrayList<>();

    final boolean acceptable = inApprovedOnlyBcFipsThread(() -> cm.isConfigurationChangeAcceptable(
        withProperty(withMacKeyFromSunJce(cfg), "getKeyWrappingTransformation", unapproved), why));

    assertThat(acceptable).isFalse();
    assertThat(why).hasSize(1);
    final String reason = why.get(0).toString();
    assertThat(why.get(0).ordinal()).as(reason).isEqualTo(ERR_CRYPTOMGR_CANNOT_GET_PREFERRED_KEY_WRAPPING_CIPHER.ordinal());
    assertThat(reason).contains("PKCS1.5");
  }

  /**
   A provider refusing to generate a MAC key with an Error, as BC-FIPS in approved-only mode
   refuses a random generator it has not approved, is reported as a refusal of the MAC algorithm.
   */
  @Test
  public void testMacKeyGenerationRefusedWithAnErrorIsReported() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final List<LocalizableMessage> why = new ArrayList<>();

    final boolean acceptable = inApprovedOnlyBcFipsThread(() -> cm.isConfigurationChangeAcceptable(
        withProperty(cfg, "getMacKeyLength", cfg.getMacKeyLength() + 64), why));

    assertThat(acceptable).isFalse();
    assertThat(why).hasSize(1);
    final String reason = why.get(0).toString();
    assertThat(why.get(0).ordinal()).as(reason).isEqualTo(ERR_CRYPTOMGR_CANNOT_GET_REQUESTED_MAC_ENGINE.ordinal());
    assertThat(reason).contains("unapproved RNG");
  }

  /**
   A provider refusing to generate a cipher key with an Error is reported as a refusal of the
   cipher transformation.
   */
  @Test
  public void testCipherKeyGenerationRefusedWithAnErrorIsReported() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final String transformation = "AES/CTR/NoPadding";
    assertThat(transformation).isNotEqualTo(cfg.getCipherTransformation());
    final List<LocalizableMessage> why = new ArrayList<>();

    final boolean acceptable = inApprovedOnlyBcFipsThread(() -> cm.isConfigurationChangeAcceptable(
        withProperty(cfg, "getCipherTransformation", transformation), why));

    assertThat(acceptable).isFalse();
    assertThat(why).hasSize(1);
    final String reason = why.get(0).toString();
    assertThat(why.get(0).ordinal()).as(reason).isEqualTo(ERR_CRYPTOMGR_CANNOT_GET_REQUESTED_ENCRYPTION_CIPHER.ordinal());
    assertThat(reason).contains("unapproved RNG");
  }

  /** The check does not need an MD5 digest, which a restricted runtime may not offer. */
  @Test
  public void testKeyWrappingCheckDoesNotNeedMd5() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final String supported = "RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING";
    assertThat(supported).isNotEqualTo(cfg.getKeyWrappingTransformation());
    final List<LocalizableMessage> why = new ArrayList<>();

    // Withdrawing MD5 withdraws the SUN provider, and with it what the rest of the check needs of
    // that provider: the SHA-1 digest of the OAEP cipher and of the default random generator the
    // cipher is initialized with, the SHA-256 digest of the HmacSHA256 MAC of SunJCE, and the
    // X.509 certificate factory.
    final Provider sunWithoutMd5 = new Provider("SunWithoutMd5", "1.0", "SUN services the check needs") {};
    sunWithoutMd5.put("MessageDigest.SHA-1", "sun.security.provider.SHA");
    sunWithoutMd5.put("Alg.Alias.MessageDigest.SHA1", "SHA-1");
    sunWithoutMd5.put("Alg.Alias.MessageDigest.SHA", "SHA-1");
    sunWithoutMd5.put("MessageDigest.SHA-256", "sun.security.provider.SHA2$SHA256");
    sunWithoutMd5.put("CertificateFactory.X.509", "sun.security.provider.X509Factory");

    withoutJceService("MessageDigest", "MD5", () ->
    {
      assertThat(cm.isConfigurationChangeAcceptable(withProperty(cfg, "getKeyWrappingTransformation", supported), why))
          .as("%s", why).isTrue();
      return null;
    }, sunWithoutMd5);
  }

  @DataProvider
  public Object[][] rsaSveSpellings()
  {
    return new Object[][] { { RsaSveKeyTransport.NAME }, { RsaSveKeyTransport.NAME.toLowerCase() } };
  }

  /**
   The RSASVE scheme is accepted where the runtime offers its algorithms, whatever the case of its
   name, as a Java transformation would be: neither the support check nor the trial wrap of the
   check asks the runtime for a cipher of that name, which no provider offers.
   */
  @Test(dataProvider = "rsaSveSpellings")
  public void testRsaSveKeyWrappingIsAccepted(final String spelling) throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final List<LocalizableMessage> why = new ArrayList<>();

    final boolean acceptable = withBcFipsFirst(() -> cm.isConfigurationChangeAcceptable(
        withProperty(cfg, "getKeyWrappingTransformation", spelling), why));

    assertThat(why).isEmpty();
    assertThat(acceptable).isTrue();
  }

  /**
   The RSASVE scheme is refused where the runtime lacks one of its algorithms, here AES-KWP as on
   Java 11 without Bouncy Castle FIPS, and the refusal names the property to set and the missing
   algorithm: the trial wrap which follows the support check would refuse it too, but without
   naming the property.
   */
  @Test
  public void testRsaSveKeyWrappingWithoutAesKwpIsRefusedNamingTheProperty() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final List<LocalizableMessage> why = new ArrayList<>();

    withoutKeyWrap(() ->
    {
      // SunJCE offers AES-KWP under that name too, and goes with AES/KWP/NoPadding.
      assertThat(Security.getProviders("Cipher.AESWrapPad")).as("providers offering AESWrapPad").isNull();
      assertThat(cm.isConfigurationChangeAcceptable(
          withProperty(cfg, "getKeyWrappingTransformation", RsaSveKeyTransport.NAME), why)).isFalse();
      return null;
    });

    assertThat(why).hasSize(1);
    assertThat(why.get(0).ordinal()).isEqualTo(ERR_CRYPTOMGR_KEY_WRAPPING_TRANSFORMATION_UNSUPPORTED.ordinal());
    assertThat(why.get(0).toString())
        .contains(RsaSveKeyTransport.NAME).contains("key-wrapping-transformation").contains("AES/KWP/NoPadding");
  }

  /**
   A key generated while the RSASVE scheme is configured is published wrapped with it, under its
   name, and unwraps to the key the data was encrypted with.
   */
  @Test
  public void testRsaSveWrappedKeyIsPublishedAndUnwraps() throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final CryptoManagerCfg rsaSve = withProperty(cfg, "getKeyWrappingTransformation", RsaSveKeyTransport.NAME);
    // No other test generates a key for this cipher and key length: the key is generated here.
    final String transformation = "AES/CBC/PKCS5Padding";
    final int keyLengthBits = 192;
    final byte[] data = "RSASVE key transport".getBytes(StandardCharsets.UTF_8);
    final String instanceKeyID = cm.getInstanceKeyID();
    // A key of the default cipher, wrapped with the default transformation, RSA-OAEP.
    cm.encrypt(data);
    final String oaepValue =
        getValueWrappedFor(instanceKeyID, cfg.getCipherTransformation(), cfg.getCipherKeyLength());
    assertThat(oaepValue.split(":")[1]).isEqualTo(cfg.getKeyWrappingTransformation());
    final byte[] oaepKey = cm.decodeSymmetricKeyAttribute(oaepValue).getEncoded();

    withBcFipsFirst(() ->
    {
      final List<LocalizableMessage> why = new ArrayList<>();
      assertThat(cm.isConfigurationChangeAcceptable(rsaSve, why)).as("%s", why).isTrue();
      cm.applyConfigurationChange(rsaSve);
      final String value;
      final SecretKey key;
      try
      {
        final byte[] cipherText = cm.encrypt(transformation, keyLengthBits, data);

        value = getValueWrappedFor(instanceKeyID, transformation, keyLengthBits);
        assertThat(value.split(":")[1]).isEqualTo(RsaSveKeyTransport.NAME);

        key = cm.decodeSymmetricKeyAttribute(value);
        // The cipher text is the prologue version, the 16-byte key identifier, the IV, then the data.
        final int ivOffset = 1 + 16;
        final int ivLength = 16;
        final Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(cipherText, ivOffset, ivLength));
        assertThat(cipher.doFinal(cipherText, ivOffset + ivLength, cipherText.length - ivOffset - ivLength))
            .isEqualTo(data);

        assertThat(cm.decodeSymmetricKeyAttribute(oaepValue).getEncoded())
            .as("RSA-OAEP value while the RSASVE scheme is configured").isEqualTo(oaepKey);
      }
      finally
      {
        cm.applyConfigurationChange(cfg);
        // The admin data backend keeps the key across restarts, where a runtime without AES-KWP,
        // such as Java 11 without Bouncy Castle FIPS, cannot unwrap it and logs an error for it.
        for (final Entry entry : getKeysForCipher(transformation, keyLengthBits))
        {
          deleteEntry(entry);
        }
      }

      // A value is unwrapped by the transformation it records, whatever the configured one, and
      // the scheme name is not case sensitive there either.
      assertThat(cm.decodeSymmetricKeyAttribute(value).getEncoded())
          .as("RSASVE value while RSA-OAEP is configured").isEqualTo(key.getEncoded());
      final String lowerCaseValue =
          value.replace(":" + RsaSveKeyTransport.NAME + ":", ":" + RsaSveKeyTransport.NAME.toLowerCase() + ":");
      assertThat(cm.decodeSymmetricKeyAttribute(lowerCaseValue).getEncoded())
          .as("RSASVE value recording the scheme name in lower case").isEqualTo(key.getEncoded());
      return null;
    });
  }

  @DataProvider
  public Object[][] keyUsages()
  {
    final String oaep = "RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING";
    final int signing = KeyUsage.digitalSignature;
    final int wrapping = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
    // Transformation, key usage, whether it is critical, whether a key is wrapped.
    return new Object[][] {
      { oaep, signing, true, false },
      { RsaSveKeyTransport.NAME, signing, true, false },
      { RsaSveKeyTransport.NAME, wrapping, true, true },
      { RsaSveKeyTransport.NAME, signing, false, true },
    };
  }

  /**
   No secret key is wrapped for an instance whose certificate has a critical key usage without
   keyEncipherment, whatever the transformation: Cipher.init refuses such a certificate for
   RSA-OAEP, and the RSASVE scheme, which takes the public key alone, checks the key usage the same
   way. A key usage which is not critical restricts nothing.
   */
  @Test(dataProvider = "keyUsages")
  public void testSecretKeyIsWrappedOnlyWhereTheKeyUsageAllowsIt(final String transformation, final int keyUsage,
      final boolean critical, final boolean wrapped) throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final CryptoManagerCfg withTransformation = withProperty(cfg, "getKeyWrappingTransformation", transformation);
    cm.encrypt("key usage".getBytes(StandardCharsets.UTF_8));
    final String value =
        getValueWrappedFor(cm.getInstanceKeyID(), cfg.getCipherTransformation(), cfg.getCipherKeyLength());
    final byte[] certificate = rsaCertificate(keyUsage, critical);
    final String keyID = CryptoManagerImpl.getInstanceKeyID(certificate);
    final DN instanceKey = DN.valueOf(ATTR_CRYPTO_KEY_ID + "=" + keyID + ",cn=instance keys,cn=admin data");
    final DN trustStoreEntry = DN.valueOf(DN_TRUST_STORE_ROOT).child(instanceKey.rdn());

    withBcFipsFirst(() ->
    {
      final List<LocalizableMessage> why = new ArrayList<>();
      assertThat(cm.isConfigurationChangeAcceptable(withTransformation, why)).as("%s", why).isTrue();
      cm.applyConfigurationChange(withTransformation);
      try
      {
        assertThat(getRootConnection().processAdd(instanceKeyEntry(instanceKey, ByteString.wrap(certificate)))
            .getResultCode()).isEqualTo(ResultCode.SUCCESS);
        if (wrapped)
        {
          assertThat(cm.reencodeSymmetricKeyAttribute(value, keyID)).startsWith(keyID + ":" + transformation + ":");
        }
        else
        {
          assertThatThrownBy(() -> cm.reencodeSymmetricKeyAttribute(value, keyID))
              .isInstanceOf(CryptoManagerException.class)
              .cause().isInstanceOf(InvalidKeyException.class).hasMessageContaining("key usage");
        }
      }
      finally
      {
        cm.applyConfigurationChange(cfg);
        getRootConnection().processDelete(instanceKey);
        getRootConnection().processDelete(trustStoreEntry);
      }
      return null;
    });
  }

  /** Returns a self-signed RSA certificate, DER encoded, with the given key usage. */
  private static byte[] rsaCertificate(final int keyUsage, final boolean critical) throws Exception
  {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "SunRsaSign");
    generator.initialize(2048);
    final KeyPair keyPair = generator.generateKeyPair();
    final Instant now = Instant.now();
    final X500Name subject = new X500Name("CN=key usage " + keyUsage);
    final JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
        BigInteger.valueOf(now.toEpochMilli()), Date.from(now.minus(1, ChronoUnit.DAYS)),
        Date.from(now.plus(1, ChronoUnit.DAYS)), subject, keyPair.getPublic());
    builder.addExtension(Extension.keyUsage, critical, new KeyUsage(keyUsage));
    return builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())).getEncoded();
  }

  /** Returns the ds-cfg-symmetric-key value of the key of a cipher which is wrapped for an instance. */
  private String getValueWrappedFor(final String instanceKeyID, final String transformation,
      final int keyLengthBits) throws DirectoryException
  {
    final Set<String> values = getKeyForCipher(transformation, keyLengthBits)
        .parseAttribute(ATTR_CRYPTO_SYMMETRIC_KEY).asSetOfString();
    return values.stream()
        .filter(v -> v.startsWith(instanceKeyID + ":"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no value wrapped for this instance in " + values));
  }

  /** The error the probe provider fails with, set by the case using it. */
  private static volatile Error probeError;

  @DataProvider
  public Object[][] errorsWhichAreNotRefusals()
  {
    final List<Object[]> cases = new ArrayList<>();
    for (final String[] check : new String[][] {
        { "getCipherTransformation", "ErrorProbe/CBC/PKCS5Padding" },
        { "getMacAlgorithm", "ErrorProbe" },
        { "getKeyWrappingTransformation", "ErrorProbeWrap/ECB/NoPadding" } })
    {
      cases.add(new Object[] { check[0], check[1], new StackOverflowError("probe") });
      cases.add(new Object[] { check[0], check[1], new ExceptionInInitializerError(new IllegalStateException("probe")) });
    }
    return cases.toArray(new Object[0][]);
  }

  /**
   An Error which is not a refusal of the configuration, of the virtual machine or of a broken
   provider jar, propagates out of the cipher, MAC and key wrapping checks with its cause.
   */
  @Test(dataProvider = "errorsWhichAreNotRefusals")
  public void testErrorWhichIsNotARefusalPropagates(final String getter, final String value, final Error error)
      throws Exception
  {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final CryptoManagerCfg cfg = getServerContext().getRootConfig().getCryptoManager();
    final Provider probe = new Provider("ErrorProbe", "1.0", "Fails with the error of the case") {};
    probe.put("KeyGenerator.ErrorProbe", FailingKeyGenerator.class.getName());
    probe.put("Cipher.ErrorProbeWrap", FailingCipher.class.getName());
    probeError = error;
    Security.insertProviderAt(probe, 1);
    try
    {
      final List<LocalizableMessage> why = new ArrayList<>();
      assertThatThrownBy(() -> cm.isConfigurationChangeAcceptable(withProperty(cfg, getter, value), why))
          .isSameAs(error);
      assertThat(why).isEmpty();
    }
    finally
    {
      Security.removeProvider("ErrorProbe");
      probeError = null;
    }
  }

  /** A key generator failing with the error of the case. */
  public static final class FailingKeyGenerator extends KeyGeneratorSpi
  {
    @Override
    protected void engineInit(final SecureRandom random)
    {
      throw probeError;
    }

    @Override
    protected void engineInit(final AlgorithmParameterSpec params, final SecureRandom random)
    {
      throw probeError;
    }

    @Override
    protected void engineInit(final int keySize, final SecureRandom random)
    {
      throw probeError;
    }

    @Override
    protected SecretKey engineGenerateKey()
    {
      throw probeError;
    }
  }

  /** A cipher which is found, and fails with the error of the case once initialized. */
  public static final class FailingCipher extends CipherSpi
  {
    @Override
    protected void engineSetMode(final String mode)
    {
      // Any mode.
    }

    @Override
    protected void engineSetPadding(final String padding)
    {
      // Any padding.
    }

    @Override
    protected int engineGetBlockSize()
    {
      return 0;
    }

    @Override
    protected int engineGetOutputSize(final int inputLen)
    {
      return 0;
    }

    @Override
    protected byte[] engineGetIV()
    {
      return null;
    }

    @Override
    protected AlgorithmParameters engineGetParameters()
    {
      return null;
    }

    @Override
    protected void engineInit(final int opmode, final Key key, final SecureRandom random)
    {
      throw probeError;
    }

    @Override
    protected void engineInit(final int opmode, final Key key, final AlgorithmParameterSpec params,
        final SecureRandom random)
    {
      throw probeError;
    }

    @Override
    protected void engineInit(final int opmode, final Key key, final AlgorithmParameters params,
        final SecureRandom random)
    {
      throw probeError;
    }

    @Override
    protected byte[] engineUpdate(final byte[] input, final int inputOffset, final int inputLen)
    {
      throw probeError;
    }

    @Override
    protected int engineUpdate(final byte[] input, final int inputOffset, final int inputLen, final byte[] output,
        final int outputOffset)
    {
      throw probeError;
    }

    @Override
    protected byte[] engineDoFinal(final byte[] input, final int inputOffset, final int inputLen)
    {
      throw probeError;
    }

    @Override
    protected int engineDoFinal(final byte[] input, final int inputOffset, final int inputLen, final byte[] output,
        final int outputOffset)
    {
      throw probeError;
    }
  }

  /**
   Returns the crypto manager configuration with a MAC algorithm which BC-FIPS does not offer in
   approved-only mode, so that SunJCE generates the MAC key the check wraps. The crypto manager
   generates keys with a random generator of the provider that came first when it was loaded, SUN
   in the test JVM, and BC-FIPS in approved-only mode refuses to generate a key with it.
   */
  private static CryptoManagerCfg withMacKeyFromSunJce(final CryptoManagerCfg cfg)
  {
    return withProperty(cfg, "getMacAlgorithm", "HmacMD5");
  }

  /**
   Runs {@code action} in a thread of its own which BC-FIPS serves in approved-only mode (a mode
   a thread cannot leave), with that provider installed first for the duration.
   */
  private static <T> T inApprovedOnlyBcFipsThread(final Callable<T> action) throws Exception
  {
    return withBcFipsFirst(() ->
    {
      final FutureTask<T> task = new FutureTask<>(() ->
      {
        assertThat(CryptoServicesRegistrar.setApprovedOnlyMode(true)).isTrue();
        return action.call();
      });
      new Thread(task, "approved-only BC-FIPS").start();
      try
      {
        return task.get(1, TimeUnit.MINUTES);
      }
      catch (ExecutionException e)
      {
        throw new AssertionError("the action failed in approved-only mode", e.getCause());
      }
    });
  }

  /**
   Returns the crypto manager configuration as it stands, with the ssl-cert-nickname
   property answering the provided nicknames, so that a change to that property is applied
   without the configuration of the running server being modified.
   */
  private static CryptoManagerCfg withSslCertNicknames(final CryptoManagerCfg cfg, final String... nicknames)
  {
    return withProperty(cfg, "getSSLCertNickname", new TreeSet<>(Arrays.asList(nicknames)));
  }

  /**
   Returns the crypto manager configuration as it stands, with the named getter answering
   the provided value instead of the configured one.
   */
  private static CryptoManagerCfg withProperty(final CryptoManagerCfg cfg, final String getter, final Object value)
  {
    return (CryptoManagerCfg) Proxy.newProxyInstance(
        CryptoManagerCfg.class.getClassLoader(),
        new Class<?>[] { CryptoManagerCfg.class },
        (proxy, method, args) -> getter.equals(method.getName()) ? value : method.invoke(cfg, args));
  }

  @Test
  public void testImportKeysUsesLatestKey()
      throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final int keyLength = 56;
    final String cipher = "DES/CFB/NoPadding";
    byte[] cipherText = cm.encrypt(cipher, keyLength, new byte[56]);
    Entry oldKey = getKeyForCipher(cipher, keyLength);
    // Force import by changing the keyID
    Modification mod = new Modification(REPLACE, create("ds-cfg-key-id", UUID.randomUUID().toString()));
    oldKey.applyModification(mod);
    cm.importCipherKeyEntry(oldKey);
    byte[] newCipherText = cm.encrypt(cipher, keyLength, new byte[56]);
    assertThat(ByteString.wrap(cipherText, 1, 16).compareTo(newCipherText, 1, 16)).isNotEqualTo(0);
  }

  private Entry getKeyForCipher(String cipher, int keyLength) throws DirectoryException
  {
    return getKeysForCipher(cipher, keyLength).get(0);
  }

  private List<? extends Entry> getKeysForCipher(String cipher, int keyLength) throws DirectoryException
  {
    SearchRequest request = newSearchRequest("cn=secret keys, cn=admin data", SearchScope.WHOLE_SUBTREE,
        "&(ds-cfg-cipher-transformation-name=" + cipher + ")(ds-cfg-key-length-bits=" + keyLength + ")");
    InternalClientConnection conn = getRootConnection();
    InternalSearchOperation search = conn.processSearch(request);
    return search.getSearchEntries();
  }

  @Test
  public void testGetInstanceKeyCertificate()
          throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final byte[] cert
            = CryptoManagerImpl.getInstanceKeyCertificateFromLocalTruststore();
    assertNotNull(cert);

    // The certificate should now be accessible in the truststore backend via LDAP.
    ByteString ldapCert;
    Options options = Options.defaultOptions()
        .set(SSL_CONTEXT, new SSLContextBuilder()
                          .setTrustManager(new BlindTrustManager())
                          .getSSLContext());
    try (LDAPConnectionFactory factory = new LDAPConnectionFactory("localhost", getServerAdminPort(), options);
        Connection conn = factory.getConnection())
    {
      conn.bind("cn=Directory Manager", "password".toCharArray());

      // TODO: should the below dn be in ConfigConstants?
      final String dnStr = "ds-cfg-key-id=ads-certificate,cn=ads-truststore";
      ConnectionEntryReader entryReader = conn.search(dnStr, BASE_OBJECT, "(objectclass=ds-cfg-instance-key)",
          "ds-cfg-public-key-certificate;binary");

      assertThat(entryReader.hasNext()).isTrue();
      SearchResultEntry searchEntry = entryReader.readEntry();
      assertThat(entryReader.hasNext()).isFalse();
      assertThat(searchEntry.getAttributeCount()).isEqualTo(1);

      final Attribute certAttr = searchEntry.getAllAttributes().iterator().next();
      // attribute ds-cfg-public-key-certificate is a MUST in the schema
      assertThat(certAttr).hasSize(1);
      ldapCert = certAttr.iterator().next();
      assertEquals(ldapCert.toByteArray(), cert);
    }

    // Compare the MD5 hash of the LDAP attribute with the one
    // retrieved from the CryptoManager.
    MessageDigest md = MessageDigest.getInstance("MD5");
    String actual = StaticUtils.bytesToHexNoSpace(md.digest(ldapCert.toByteArray()));
    assertEquals(actual, cm.getInstanceKeyID());

    // Call twice to ensure idempotent.
    CryptoManagerImpl.publishInstanceKeyEntryInADS();
    CryptoManagerImpl.publishInstanceKeyEntryInADS();
  }

  @Test
  public void testMacSuccess()
          throws Exception {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String text = "1234";

    final String macKeyID = cm.getMacEngineKeyEntryID();

    final Mac signingMac = cm.getMacEngine(macKeyID);
    final byte[] signedHash = signingMac.doFinal(text.getBytes());

    final Mac validatingMac = cm.getMacEngine(macKeyID);
    final byte[] calculatedSignature = validatingMac.doFinal(text.getBytes());

    assertTrue(Arrays.equals(calculatedSignature, signedHash));
  }

  // TODO: other-than-default MAC

  private class CipherParameters {
    private final String fAlgorithm;
    private final String fMode;
    private final String fPadding;
    private final int fKeyLength;

    public CipherParameters(final String algorithm, final String mode,
                            final String padding, final int keyLength) {
      fAlgorithm = algorithm;
      fMode = mode;
      fPadding = padding;
      fKeyLength = keyLength;
    }

    public String getTransformation() {
      if (null != fAlgorithm)
      {
        return fMode != null
                  ? fAlgorithm + "/" + fMode + "/" + fPadding
                  : fAlgorithm;
      }
      return null;
    }

    public int getKeyLength() {
      return fKeyLength;
    }
  }


  /**
   Cipher parameter data set.

   @return The set of Cipher parameters with which to test.
   */
  @DataProvider(name = "cipherParametersData")
  public Object[][] cipherParametersData() {
    return new Object[][] {
      // default (preferred) AES/CBC/PKCS5Padding 128bit key.
      { new CipherParameters(null, null, null, 128) },
      // custom
// TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2448
// TODO: { new CipherParameters("Blowfish", "CFB", "NoPadding", 448) },
// TODO: { new CipherParameters("AES", "CBC", "PKCS5Padding", 256) },
      { new CipherParameters("AES", "CFB", "NoPadding", 128) },
      { new CipherParameters("Blowfish", "CFB", "NoPadding", 128) },
      { new CipherParameters("RC4", null, null, 104) },
      { new CipherParameters("RC4", "NONE", "NoPadding", 128) },
      { new CipherParameters("DES", "CFB", "NoPadding", 56) },
      { new CipherParameters("DESede", "ECB", "PKCS5Padding", 168) },
    };
  }


  /**
   Tests a simple encryption-decryption cycle using the supplied cipher
   parameters.

   @param cp  Cipher parameters to use for this test iteration.

   @throws Exception If an exceptional condition arises.
   */
@Test(dataProvider="cipherParametersData")
  public void testEncryptDecryptSuccess(CipherParameters cp)
          throws Exception {
    final CryptoManager cm = getServerContext().getCryptoManager();
    final String secretMessage = "1234";

    final byte[] cipherText = (null == cp.getTransformation())
            ? cm.encrypt(secretMessage.getBytes()) // default
            : cm.encrypt(cp.getTransformation(), cp.getKeyLength(),
                         secretMessage.getBytes());
    assertEquals(-1, new String(cipherText).indexOf(secretMessage));

    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);
  }


  /**
   Tests a simple cipher stream encryption-decryption cycle using the supplied
   cipher parameters.

   @param cp  Cipher parameters to use for this test iteration.

   @throws Exception If an exceptional condition arises.
   */
  @Test(dataProvider="cipherParametersData")
  public void testStreamEncryptDecryptSuccess(CipherParameters cp)
          throws Exception {
    final CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "56789";

    final File tempFile
            = File.createTempFile(cm.getClass().getName(), null);
    tempFile.deleteOnExit();

    OutputStream os = new FileOutputStream(tempFile);
    os = (null == cp.getTransformation())
            ? cm.getCipherOutputStream(os) // default
            : cm.getCipherOutputStream(cp.getTransformation(), cp.getKeyLength(), os);
    os.write(secretMessage.getBytes());
    os.close();

    // TODO: check tempfile for plaintext.

    InputStream is = new FileInputStream(tempFile);
    is = cm.getCipherInputStream(is);
    byte[] plainText = new byte[secretMessage.getBytes().length];
    assertEquals(is.read(plainText), secretMessage.getBytes().length);
    assertEquals(is.read(), -1);
    is.close();
    assertEquals(new String(plainText), secretMessage);
  }

  /**
   Tests to ensure the same key identifier (and hence, key) is used for
   successive encryptions specifying the same algorithm and key length.
   <p>
   The default encryption cipher requires an initialization vector. Confirm
   successive uses of a key produces distinct ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test
  public void testKeyEntryReuse()
          throws Exception {

    final CryptoManager cm = TestCaseUtils.getServerContext().getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt(secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt(secretMessage.getBytes());

    // test cycle
    final byte[] plainText = cm.decrypt(cipherText2);
    assertEquals(new String(plainText), secretMessage);

    // test for identical keys
    final byte[] keyID = Arrays.copyOfRange(cipherText, 1, 16);
    final byte[] keyID2 = Arrays.copyOfRange(cipherText2, 1, 16);
    assertTrue(Arrays.equals(keyID, keyID2));

    // test for distinct ciphertext
    assertFalse(Arrays.equals(cipherText, cipherText2));
  }


  /**
   Test that secret keys are persisted: Encrypt some data using a
   variety of transformations, restart the instance, and decrypt the
   retained ciphertext.

   @throws Exception  In case an error occurs in the encryption routine.
   */
  @Test
  public void testKeyPersistence()
        throws Exception {
    final CryptoManager cm = getServerContext().getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";

    final byte[] cipherText = cm.encrypt("Blowfish/CFB/NoPadding", 128,
            secretMessage.getBytes());
    final byte[] cipherText2 = cm.encrypt("RC4", 104,
            secretMessage.getBytes());

    TestCaseUtils.getServer().restart(this.getClass().getName(),
        LocalizableMessage.raw("CryptoManager: testing persistent secret keys."));

    byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);
    plainText = cm.decrypt(cipherText2);
    assertEquals(new String(plainText), secretMessage);
  }


  /**
   Mark a key compromised; ensure 1) subsequent encryption requests use a
   new key; 2) ciphertext produced using the compromised key can still be
   decrypted; 3) once the compromised key entry is removed, confirm ciphertext
   produced using the compromised key can no longer be decrypted.

   @throws Exception In case something exceptional happens.
   */
  @Test
  public void testCompromisedKey() throws Exception {
    final CryptoManager cm = getServerContext().getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";
    final String cipherTransformationName = "AES/CBC/PKCS5Padding";
    final int cipherKeyLength = 128;

    // Initial encryption ensures a cipher key entry is in ADS.
    final byte[] cipherText = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // Retrieve all uncompromised cipher key entries corresponding to the
    // specified transformation and key length. Mark each entry compromised.
    final String baseDNStr // TODO: is this DN defined elsewhere as a constant?
            = "cn=secret keys," + ADSContext.getAdministrationSuffixDN();
    final DN baseDN = DN.valueOf(baseDNStr);
    final String FILTER_OC_INSTANCE_KEY = "(objectclass=" + OC_CRYPTO_CIPHER_KEY + ")";
    final String FILTER_NOT_COMPROMISED =
        "(!(" + ATTR_CRYPTO_KEY_COMPROMISED_TIME + "=*))";
    final String FILTER_CIPHER_TRANSFORMATION_NAME =
        "(" + ATTR_CRYPTO_CIPHER_TRANSFORMATION_NAME + "=" + cipherTransformationName + ")";
    final String FILTER_CIPHER_KEY_LENGTH =
        "(" + ATTR_CRYPTO_KEY_LENGTH_BITS + "=" + cipherKeyLength + ")";
    final String searchFilter =
        "(&"
        + FILTER_OC_INSTANCE_KEY
        + FILTER_NOT_COMPROMISED
        + FILTER_CIPHER_TRANSFORMATION_NAME
        + FILTER_CIPHER_KEY_LENGTH
        + ")";
    final SearchRequest request = newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, searchFilter).addAttribute("dn");
    InternalSearchOperation searchOp = getRootConnection().processSearch(request);
    assertFalse(searchOp.getSearchEntries().isEmpty());

    String compromisedTime = TimeThread.getGeneralizedTime();
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(true,
        "dn: " + e.getName(),
        "changetype: modify",
        "replace: " + ATTR_CRYPTO_KEY_COMPROMISED_TIME,
        ATTR_CRYPTO_KEY_COMPROMISED_TIME + ": " + compromisedTime);
    }
    //Wait so the above asynchronous modification can be applied. The crypto
    //manager's cipherKeyEntryCache needs to be updated before the encrypt()
    //method is called below.
    Thread.sleep(1000);
    // Use the transformation and key length again. A new cipher key
    // should be produced.
    final byte[] cipherText2 = cm.encrypt(cipherTransformationName,
            cipherKeyLength, secretMessage.getBytes());

    // 1. Test for distinct keys.
    final byte[] keyID = new byte[16];
    final byte[] keyID2 = new byte[16];
    System.arraycopy(cipherText, 1, keyID, 0, 16);
    System.arraycopy(cipherText2, 1, keyID2, 0, 16);
    assertFalse(Arrays.equals(keyID, keyID2));

    // 2. Confirm ciphertext produced using the compromised key can still be decrypted.
    final byte[] plainText = cm.decrypt(cipherText);
    assertEquals(new String(plainText), secretMessage);

    // 3. Delete the compromised entry(ies) and ensure ciphertext produced
    // using a compromised key can no longer be decrypted.
    for (Entry e : searchOp.getSearchEntries()) {
      TestCaseUtils.applyModifications(true, "dn: " + e.getName(), "changetype: delete");
    }
    Thread.sleep(1000); // Clearing the cache is asynchronous.
    try {
      cm.decrypt(cipherText);
    }
    catch (CryptoManagerException ex) {
      // TODO: if reasons are added to CryptoManagerException, check for
      // expected cause.
    }
  }

  /**
   TODO: Test shared secret key wrapping (various wrapping ciphers, if configurable).
   */


  /**
   TODO: Test the secret key synchronization protocol.

     1. Create the first instance; add reversible password storage scheme
     to password policy; add entry using explicit password policy; confirm
     secret key entry has been produced.

     2. Create and initialize the second instance into the existing ADS domain.
     The secret key entries should be propagated to the second instance via
     replication. Then the new instance should detect that the secret key
     entries are missing ds-cfg-symmetric-key attribute values for that
     instance, inducing the key synchronization protocol.

     3. Confirm the second instance can decrypt the password of the entry
     added in step 1; e.g., bind as that user.

     4. Stop the second instance. At the first instance, enable a different
     reversible password storage scheme (different cipher transformation,
     and hence secret key entry); add another entry using that password
     storage scheme; start the second instance; ensure the password can
     be decrypted at the second instance.
     */
}
