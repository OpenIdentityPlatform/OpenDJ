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

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ByteString.valueOfBase64;
import static org.forgerock.opendj.security.KeyStoreObject.dnOf;
import static org.forgerock.opendj.security.KeyStoreObject.newKeyObject;
import static org.forgerock.opendj.security.KeyStoreObject.newTrustedCertificateObject;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.KeyStoreTestUtils.*;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.util.Options.defaultOptions;

import java.security.Key;
import java.security.PrivateKey;

import javax.crypto.SecretKey;

import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class KeyStoreObjectTest extends SdkTestCase {
    private static final KeyProtector KEY_PROTECTOR =
            new KeyProtector(defaultOptions().set(GLOBAL_PASSWORD, newClearTextPasswordFactory(KEYSTORE_PASSWORD)));

    @Test
    public void testNewTrustedCertificateEntry() throws Exception {
        // Check constructed trusted certificate.
        final KeyStoreObject keyStoreObject = newTrustedCertificateObject(TEST_ALIAS, PUBLIC_KEY_CERTIFICATE);
        validateTrustedCertificateKeyStoreEntry(keyStoreObject);

        // Check LDAP encoding.
        final Entry ldapEntry = keyStoreObject.toLDAPEntry(KEYSTORE_DN);
        assertThat((Object) ldapEntry.getName()).isEqualTo(TEST_DN);
        assertThat(ldapEntry.parseAttribute("objectClass").asSetOfString())
                .containsOnly("top", "ds-keystore-object", "ds-keystore-trusted-certificate");
        assertThat(ldapEntry.parseAttribute("ds-keystore-alias").asString()).isEqualTo(TEST_ALIAS);
        assertThat(ldapEntry.parseAttribute("ds-keystore-certificate;binary")
                            .asCertificate()).isEqualTo(PUBLIC_KEY_CERTIFICATE);
        validateTrustedCertificateKeyStoreEntry(KeyStoreObject.valueOf(ldapEntry));
    }

    private void validateTrustedCertificateKeyStoreEntry(final KeyStoreObject keyStoreObject) throws Exception {
        assertThat(keyStoreObject.getAlias()).isEqualTo(TEST_ALIAS);
        assertThat(keyStoreObject.isTrustedCertificate()).isTrue();
        assertThat(keyStoreObject.getCreationDate()).isNotNull();
        assertThat(keyStoreObject.getCertificate()).isSameAs(PUBLIC_KEY_CERTIFICATE);
        assertThat(keyStoreObject.getCertificateChain()).isNull();
        assertThat(keyStoreObject.getKey(new KeyProtector(defaultOptions()), KEY_PASSWORD)).isNull();
    }

    @Test
    public void testNewPrivateKeyEntry() throws Exception {
        // Check constructed private key.
        final KeyStoreObject keyStoreObject =
                newKeyObject(TEST_ALIAS, PRIVATE_KEY, CERTIFICATE_CHAIN, KEY_PROTECTOR, KEY_PASSWORD);
        validatePrivateKeyStoreEntry(keyStoreObject);

        // Check LDAP encoding.
        final Entry ldapEntry = keyStoreObject.toLDAPEntry(KEYSTORE_DN);
        assertThat((Object) ldapEntry.getName()).isEqualTo(TEST_DN);
        assertThat(ldapEntry.parseAttribute("objectClass").asSetOfString()).containsOnly("top",
                                                                                         "ds-keystore-object",
                                                                                         "ds-keystore-private-key");
        assertThat(ldapEntry.parseAttribute("ds-keystore-alias").asString()).isEqualTo(TEST_ALIAS);
        assertThat(ldapEntry.parseAttribute("ds-keystore-key-algorithm").asString()).isEqualTo("RSA");

        // Just check that these attributes are present for now. Their content will be validated in the next step.
        assertThat(ldapEntry.containsAttribute("ds-keystore-certificate;binary")).isTrue();
        assertThat(ldapEntry.containsAttribute("ds-keystore-certificate-chain")).isFalse();
        assertThat(ldapEntry.containsAttribute("ds-keystore-key")).isTrue();
        validatePrivateKeyStoreEntry(KeyStoreObject.valueOf(ldapEntry));
    }

    private void validatePrivateKeyStoreEntry(final KeyStoreObject keyStoreObject) throws Exception {
        assertThat(keyStoreObject.getAlias()).isEqualTo(TEST_ALIAS);
        assertThat(keyStoreObject.isTrustedCertificate()).isFalse();
        assertThat(keyStoreObject.getCreationDate()).isNotNull();
        assertThat(keyStoreObject.getCertificate()).isEqualTo(PUBLIC_KEY_CERTIFICATE);
        assertThat(keyStoreObject.getCertificateChain()).containsExactly(CERTIFICATE_CHAIN);
        final Key privateKey = keyStoreObject.getKey(KEY_PROTECTOR, KEY_PASSWORD);
        assertThat(privateKey).isInstanceOf(PrivateKey.class);
        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(privateKey.getEncoded()).isEqualTo(valueOfBase64(PRIVATE_KEY_ENCODED_B64).toByteArray());
    }

    @Test
    public void testNewSecretKeyEntry() throws Exception {
        // Check constructed secret key.
        final SecretKey secretKey = createSecretKey();
        final KeyStoreObject keyStoreObject =
                newKeyObject(TEST_ALIAS, secretKey, CERTIFICATE_CHAIN, KEY_PROTECTOR, KEY_PASSWORD);
        validateSecretKeyStoreEntry(keyStoreObject, secretKey.getEncoded());

        // Check LDAP encoding.
        final Entry ldapEntry = keyStoreObject.toLDAPEntry(KEYSTORE_DN);
        assertThat((Object) ldapEntry.getName()).isEqualTo(TEST_DN);
        assertThat(ldapEntry.parseAttribute("objectClass").asSetOfString()).containsOnly("top",
                                                                                         "ds-keystore-object",
                                                                                         "ds-keystore-secret-key");
        assertThat(ldapEntry.parseAttribute("ds-keystore-alias").asString()).isEqualTo(TEST_ALIAS);
        assertThat(ldapEntry.parseAttribute("ds-keystore-key-algorithm").asString()).isEqualTo("PBKDF2WithHmacSHA1");

        // Just check that these attributes are present for now. Their content will be validated in the next step.
        assertThat(ldapEntry.containsAttribute("ds-keystore-key")).isTrue();
        validateSecretKeyStoreEntry(KeyStoreObject.valueOf(ldapEntry), secretKey.getEncoded());
    }

    private void validateSecretKeyStoreEntry(KeyStoreObject keyStoreObject, byte[] encodedKey) throws Exception {
        assertThat(keyStoreObject.getAlias()).isEqualTo(TEST_ALIAS);
        assertThat(keyStoreObject.isTrustedCertificate()).isFalse();
        assertThat(keyStoreObject.getCreationDate()).isNotNull();
        assertThat(keyStoreObject.getCertificate()).isNull();
        assertThat(keyStoreObject.getCertificateChain()).isNull();
        final Key secretKey = keyStoreObject.getKey(KEY_PROTECTOR, KEY_PASSWORD);
        assertThat(secretKey).isInstanceOf(SecretKey.class);
        assertThat(secretKey.getAlgorithm()).isEqualTo("PBKDF2WithHmacSHA1");
        assertThat(secretKey.getFormat()).isEqualTo("RAW");
        assertThat(secretKey.getEncoded()).isEqualTo(encodedKey);
    }

    @Test
    public void testDnOf() throws Exception {
        assertThat((Object) dnOf(KEYSTORE_DN, TEST_ALIAS)).isEqualTo(TEST_DN);
    }
}
