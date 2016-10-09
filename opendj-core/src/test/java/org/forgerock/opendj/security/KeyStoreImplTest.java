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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.KeyStoreParameters.newKeyStoreParameters;
import static org.forgerock.opendj.security.KeyStoreTestUtils.*;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.util.Options.defaultOptions;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.crypto.SecretKey;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Options;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class KeyStoreImplTest extends SdkTestCase {
    private MemoryBackend backend;
    private KeyStore keyStore;

    @BeforeClass
    public void beforeClass() {
        Schema.setDefaultSchema(OpenDJProviderSchema.SCHEMA);
    }

    @BeforeMethod
    public void beforeMethod() throws Exception {
        backend = createKeyStoreMemoryBackend();
        keyStore = createKeyStore(backend);
    }

    // Test key store operations.

    @Test
    public void getProviderShouldReturnOpenDJProvider() {
        assertThat(keyStore.getProvider()).isInstanceOf(OpenDJProvider.class);
    }

    @Test
    public void getTypeShouldReturnLDAP() {
        assertThat(keyStore.getType()).isEqualTo("LDAP");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void storeShouldThrowWhenOutputStreamIsNotNull() throws Exception {
        keyStore.store(mock(OutputStream.class), null);
    }

    @Test
    public void storeShouldBeNoOpWhenOutputStreamIsNull() throws Exception {
        keyStore.store(null, null);
    }

    @Test
    public void storeShouldBeNoOp() throws Exception {
        keyStore.store(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void loadWithNonNullInputStreamShouldThrow() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("LDAP", new OpenDJProvider());
        keyStore.load(mock(InputStream.class), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void loadWithNullInputStreamShouldThrowWhenNoProviderConfig() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("LDAP", new OpenDJProvider());
        keyStore.load(null, null);
    }

    @Test
    public void loadWithNullInputStreamShouldUseProviderConfig() throws Exception {
        final ConnectionFactory factory = newInternalConnectionFactory(backend);
        final Options options = defaultOptions().set(GLOBAL_PASSWORD, newClearTextPasswordFactory(KEYSTORE_PASSWORD));
        final KeyStoreParameters config = newKeyStoreParameters(factory, KEYSTORE_DN, options);
        final OpenDJProvider provider = new OpenDJProvider(config);
        final KeyStore keyStore = KeyStore.getInstance("LDAP", provider);

        keyStore.load(null, null);

        assertThat(keyStore.size()).isEqualTo(0);
        assertThat(backend.size()).isEqualTo(1);
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, null);
        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(backend.size()).isEqualTo(2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void loadWithNullLoadStoreParameterShouldThrowWhenNoProviderConfig() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("LDAP", new OpenDJProvider());
        keyStore.load(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void loadWithNullLoadStoreParameterShouldThrowWhenParametersHaveWrongType() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("LDAP", new OpenDJProvider());
        keyStore.load(mock(LoadStoreParameter.class));
    }

    @Test
    public void loadWithNullLoadStoreParameterShouldUseProviderConfig() throws Exception {
        final ConnectionFactory factory = newInternalConnectionFactory(backend);
        final Options options = defaultOptions().set(GLOBAL_PASSWORD, newClearTextPasswordFactory(KEYSTORE_PASSWORD));
        final KeyStoreParameters config = newKeyStoreParameters(factory, KEYSTORE_DN, options);
        final OpenDJProvider provider = new OpenDJProvider(config);
        final KeyStore keyStore = KeyStore.getInstance("LDAP", provider);

        keyStore.load(null);

        assertThat(keyStore.size()).isEqualTo(0);
        assertThat(backend.size()).isEqualTo(1);
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, null);
        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(backend.size()).isEqualTo(2);
    }

    @Test
    public void loadWithNonNullLoadStoreParameterShouldNotUseProviderConfig() throws Exception {
        final ConnectionFactory factory = newInternalConnectionFactory(backend);
        final Options options = defaultOptions().set(GLOBAL_PASSWORD, newClearTextPasswordFactory(KEYSTORE_PASSWORD));
        final KeyStoreParameters config = newKeyStoreParameters(factory, KEYSTORE_DN, options);
        final KeyStore keyStore = KeyStore.getInstance("LDAP", new OpenDJProvider());

        keyStore.load(config);

        assertThat(keyStore.size()).isEqualTo(0);
        assertThat(backend.size()).isEqualTo(1);
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, null);
        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(backend.size()).isEqualTo(2);
    }

    // Test keys and certificate management happy paths.

    @Test
    public void secretKeysCanBeStoredAndRetrieved() throws Exception {
        final SecretKey key = createSecretKey();
        keyStore.setKeyEntry(TEST_ALIAS, key, KEY_PASSWORD, null);

        final Key retrievedKey = keyStore.getKey(TEST_ALIAS, KEY_PASSWORD);
        assertThat(retrievedKey).isNotNull();
        assertThat(retrievedKey).isInstanceOf(SecretKey.class);
        assertThat(retrievedKey.getAlgorithm()).isEqualTo(key.getAlgorithm());
        assertThat(retrievedKey.getFormat()).isEqualTo(key.getFormat());
        assertThat(retrievedKey.getEncoded()).isEqualTo(key.getEncoded());

        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(Collections.list(keyStore.aliases())).containsExactly(TEST_ALIAS);
        assertThat(keyStore.containsAlias(TEST_ALIAS));
        assertThat(keyStore.getCertificate(TEST_ALIAS)).isNull();
        assertThat(keyStore.getCertificateChain(TEST_ALIAS)).isNull();
        assertThat(keyStore.entryInstanceOf(TEST_ALIAS, SecretKeyEntry.class));
        assertThat(keyStore.getCreationDate(TEST_ALIAS)).isNotNull();
        assertThat(keyStore.getEntry(TEST_ALIAS, newPasswordProtection())).isInstanceOf(SecretKeyEntry.class);
        assertThat(keyStore.isCertificateEntry(TEST_ALIAS)).isFalse();
        assertThat(keyStore.isKeyEntry(TEST_ALIAS)).isTrue();
    }

    private static PasswordProtection newPasswordProtection() {
        return new PasswordProtection(KEY_PASSWORD.clone());
    }

    @Test
    public void privateKeysCanBeStoredAndRetrieved() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, PRIVATE_KEY, KEY_PASSWORD, CERTIFICATE_CHAIN);

        final Key retrievedKey = keyStore.getKey(TEST_ALIAS, KEY_PASSWORD);
        assertThat(retrievedKey).isNotNull();
        assertThat(retrievedKey).isInstanceOf(PrivateKey.class);
        assertThat(retrievedKey.getAlgorithm()).isEqualTo(PRIVATE_KEY.getAlgorithm());
        assertThat(retrievedKey.getFormat()).isEqualTo(PRIVATE_KEY.getFormat());
        assertThat(retrievedKey.getEncoded()).isEqualTo(PRIVATE_KEY.getEncoded());

        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(Collections.list(keyStore.aliases())).containsExactly(TEST_ALIAS);
        assertThat(keyStore.containsAlias(TEST_ALIAS));
        assertThat(keyStore.getCertificate(TEST_ALIAS)).isSameAs(PUBLIC_KEY_CERTIFICATE);
        assertThat(keyStore.getCertificateChain(TEST_ALIAS)).isNotSameAs(CERTIFICATE_CHAIN); // should defensive copy
        assertThat(keyStore.getCertificateChain(TEST_ALIAS)).containsExactly(PUBLIC_KEY_CERTIFICATE);
        assertThat(keyStore.entryInstanceOf(TEST_ALIAS, PrivateKeyEntry.class));
        assertThat(keyStore.getCreationDate(TEST_ALIAS)).isNotNull();
        assertThat(keyStore.getEntry(TEST_ALIAS, newPasswordProtection())).isInstanceOf(PrivateKeyEntry.class);
        assertThat(keyStore.isCertificateEntry(TEST_ALIAS)).isFalse();
        assertThat(keyStore.isKeyEntry(TEST_ALIAS)).isTrue();
    }

    @Test
    public void trustedCertificatesCanBeStoredAndRetrieved() throws Exception {
        keyStore.setCertificateEntry(TEST_ALIAS, PUBLIC_KEY_CERTIFICATE);

        final Certificate retrievedCertificate = keyStore.getCertificate(TEST_ALIAS);
        assertThat(retrievedCertificate).isNotNull();
        assertThat(retrievedCertificate).isInstanceOf(X509Certificate.class);
        assertThat(retrievedCertificate).isEqualTo(PUBLIC_KEY_CERTIFICATE);

        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(Collections.list(keyStore.aliases())).containsExactly(TEST_ALIAS);
        assertThat(keyStore.containsAlias(TEST_ALIAS));
        assertThat(keyStore.getCertificateChain(TEST_ALIAS)).isNull();
        assertThat(keyStore.entryInstanceOf(TEST_ALIAS, TrustedCertificateEntry.class));
        assertThat(keyStore.getCreationDate(TEST_ALIAS)).isNotNull();
        assertThat(keyStore.getEntry(TEST_ALIAS, null)).isInstanceOf(TrustedCertificateEntry.class);
        assertThat(keyStore.isCertificateEntry(TEST_ALIAS)).isTrue();
        assertThat(keyStore.isKeyEntry(TEST_ALIAS)).isFalse();
    }

    // Test keys and certificate management edge cases.

    @Test
    public void getKeyShouldReturnNullWhenAliasUnknown() throws Exception {
        final Key retrievedKey = keyStore.getKey(TEST_ALIAS, KEY_PASSWORD);
        assertThat(retrievedKey).isNull();
    }

    @Test(expectedExceptions = UnrecoverableKeyException.class)
    public void getKeyShouldThrowWhenPasswordIsMissing() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, null);
        keyStore.getKey(TEST_ALIAS, null);
    }

    @Test(expectedExceptions = UnrecoverableKeyException.class)
    public void getKeyShouldThrowWhenPasswordIsBad() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, null);
        keyStore.getKey(TEST_ALIAS, "bad".toCharArray());
    }

    @Test
    public void setKeyEntryWithSecretKeyWithCertChainIsAllowed() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, CERTIFICATE_CHAIN);
        assertThat(keyStore.isKeyEntry(TEST_ALIAS)).isTrue();
        assertThat(keyStore.getCertificate(TEST_ALIAS)).isNull();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void setKeyEntryShouldThrowWhenPrivateKeyWithoutCertChain() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, PRIVATE_KEY, KEY_PASSWORD, null);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void setKeyEntryWithPreEncodedKeyIsNotSupported() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey().getEncoded(), null);
    }

    @Test
    public void keyStoreCanManageMultipleObjects() throws Exception {
        final String[] aliases = { "cert1", "cert2", "pkey", "skey1", "skey2" };

        keyStore.setCertificateEntry("cert1", PUBLIC_KEY_CERTIFICATE);
        keyStore.setCertificateEntry("cert2", TRUSTED_CERTIFICATE);
        keyStore.setKeyEntry("pkey", PRIVATE_KEY, KEY_PASSWORD, CERTIFICATE_CHAIN);
        keyStore.setKeyEntry("skey1", createSecretKey(), KEY_PASSWORD, null);
        keyStore.setKeyEntry("skey2", createSecretKey(), KEY_PASSWORD, null);

        assertThat(Collections.list(keyStore.aliases())).containsOnly(aliases);
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            assertThat(keyStore.size()).isEqualTo(5 - i);
            assertThat(keyStore.containsAlias(alias)).isTrue();
            keyStore.deleteEntry(alias);
            assertThat(keyStore.containsAlias(alias)).isFalse();
        }
        assertThat(keyStore.size()).isEqualTo(0);
        assertThat(Collections.list(keyStore.aliases())).isEmpty();
    }

    @Test
    public void deleteEntryShouldIgnoreMissingAliases() throws Exception {
        keyStore.deleteEntry("unknown");
    }

    @Test
    public void getCertificateAliasShouldPerformCertificateMatchSearches() throws Exception {
        keyStore.setKeyEntry("privateKey", PRIVATE_KEY, KEY_PASSWORD, CERTIFICATE_CHAIN);
        keyStore.setCertificateEntry("trustedCertificate", TRUSTED_CERTIFICATE);

        assertThat(keyStore.getCertificateAlias(PUBLIC_KEY_CERTIFICATE)).isEqualTo("privateKey");
        assertThat(keyStore.getCertificateAlias(TRUSTED_CERTIFICATE)).isEqualTo("trustedCertificate");

        keyStore.deleteEntry("privateKey");
        assertThat(keyStore.getCertificateAlias(PUBLIC_KEY_CERTIFICATE)).isNull();

        keyStore.deleteEntry("trustedCertificate");
        assertThat(keyStore.getCertificateAlias(TRUSTED_CERTIFICATE)).isNull();
    }

    @Test
    public void setKeyShouldReplaceExistingObjects() throws Exception {
        keyStore.setKeyEntry(TEST_ALIAS, PRIVATE_KEY, KEY_PASSWORD, CERTIFICATE_CHAIN);
        assertThat(keyStore.getKey(TEST_ALIAS, KEY_PASSWORD)).isInstanceOf(PrivateKey.class);

        keyStore.setKeyEntry(TEST_ALIAS, createSecretKey(), KEY_PASSWORD, null);
        assertThat(keyStore.getKey(TEST_ALIAS, KEY_PASSWORD)).isInstanceOf(SecretKey.class);
    }

    @Test
    public void setCertificateShouldReplaceExistingCertificates() throws Exception {
        keyStore.setCertificateEntry(TEST_ALIAS, PUBLIC_KEY_CERTIFICATE);
        assertThat(keyStore.getCertificate(TEST_ALIAS)).isEqualTo(PUBLIC_KEY_CERTIFICATE);

        keyStore.setCertificateEntry(TEST_ALIAS, TRUSTED_CERTIFICATE);
        assertThat(keyStore.getCertificate(TEST_ALIAS)).isEqualTo(TRUSTED_CERTIFICATE);
    }
}
