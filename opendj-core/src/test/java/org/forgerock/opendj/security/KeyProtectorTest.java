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
import static org.forgerock.opendj.security.KeyStoreParameters.EXTERNAL_KEY_WRAPPING_STRATEGY;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.KeyStoreTestUtils.PRIVATE_KEY;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.util.Options.defaultOptions;

import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class KeyProtectorTest extends SdkTestCase {
    // Not multiple of 8 bytes - needs padding for AESWrap.
    private static final ByteString PLAIN_KEY_NEEDS_PADDING = ByteString.valueOfUtf8("123456781234");
    // Multiple of 8 bytes - doesn't need padding for AESWrap.
    private static final ByteString PLAIN_KEY = ByteString.valueOfUtf8("1234567812345678");
    private static final char[] KEYSTORE_PASSWORD = "keystore password".toCharArray();
    private static final char[] KEY_PASSWORD = "key password".toCharArray();
    private static final char[] BAD_PASSWORD = "bad password".toCharArray();
    // Fake external protection mechanism which just performs base 64 encoding/decoding.
    private static final ExternalKeyWrappingStrategy TEST_STRATEGY = new ExternalKeyWrappingStrategy() {
        @Override
        public ByteSequence wrapKey(final ByteSequence unwrappedKey) throws LocalizedKeyStoreException {
            return ByteString.valueOfUtf8(unwrappedKey.toBase64String());
        }

        @Override
        public ByteSequence unwrapKey(final ByteSequence wrappedKey) throws LocalizedKeyStoreException {
            return ByteString.valueOfBase64(wrappedKey.toString());
        }
    };

    @BeforeClass
    public void sanityCheckTestFramework() throws Exception {
        assertThat(encodedKeyIsEncrypted(PLAIN_KEY)).isFalse();
        assertThat(encodedKeyIsEncrypted(new ByteStringBuilder().appendBytes(PLAIN_KEY)
                                                                .appendUtf8("tail")
                                                                .toByteString())).isFalse();
        assertThat(encodedKeyIsEncrypted(new ByteStringBuilder().appendUtf8("head")
                                                                .appendBytes(PLAIN_KEY)
                                                                .toByteString())).isFalse();
        assertThat(encodedKeyIsEncrypted(new ByteStringBuilder().appendUtf8("head")
                                                                .appendBytes(PLAIN_KEY)
                                                                .appendUtf8("tail")
                                                                .toByteString())).isFalse();
        assertThat(encodedKeyIsEncrypted(ByteString.valueOfUtf8("different"))).isTrue();
    }

    @Test
    public void shouldEncodeAndDecodeWithNoProtection() throws Exception {
        final KeyProtector kp = new KeyProtector(defaultOptions());
        final ByteString encodedKey = kp.encodeKey(secretKey(PLAIN_KEY), null);
        assertThat(encodedKey).isNotEqualTo(PLAIN_KEY);
        assertThat(encodedKeyIsEncrypted(encodedKey)).isFalse();
        assertThat(kp.decodeSecretKey(encodedKey, "RAW", null).getEncoded()).isEqualTo(PLAIN_KEY.toByteArray());
    }

    @Test
    public void shouldEncodeAndDecodeWithIndividualPassword() throws Exception {
        shouldEncodeAndDecodeAndBeEncrypted(new KeyProtector(defaultOptions()), KEY_PASSWORD);
    }

    @Test
    public void shouldEncodeAndDecodeWithKeyStorePasswordOnly() throws Exception {
        shouldEncodeAndDecodeAndBeEncrypted(forPasswordProtectedKeyStore(KEYSTORE_PASSWORD), null);
    }

    private static KeyProtector forPasswordProtectedKeyStore(final char[] keystorePassword) {
        return new KeyProtector(defaultOptions().set(GLOBAL_PASSWORD, newClearTextPasswordFactory(keystorePassword)));
    }

    @Test
    public void shouldEncodeAndDecodeWithKeyStorePasswordAndIndividualPassword() throws Exception {
        shouldEncodeAndDecodeAndBeEncrypted(forPasswordProtectedKeyStore(KEYSTORE_PASSWORD), KEY_PASSWORD);
    }

    private static KeyProtector forExternallyProtectedKeyStore() {
        return new KeyProtector(defaultOptions().set(EXTERNAL_KEY_WRAPPING_STRATEGY, TEST_STRATEGY));
    }

    @Test
    public void shouldEncodeAndDecodeWithExternalMechanismOnly() throws Exception {
        shouldEncodeAndDecodeAndBeEncrypted(forExternallyProtectedKeyStore(), null);
    }

    @Test
    public void shouldEncodeAndDecodeWithExternalMechanismAndIndividualPassword() throws Exception {
        shouldEncodeAndDecodeAndBeEncrypted(forExternallyProtectedKeyStore(), KEY_PASSWORD);
    }

    @Test(expectedExceptions = LocalizedKeyStoreException.class)
    public void shouldFailWhenDecodeWithWrongIndividualPassword() throws Exception {
        final KeyProtector kpEncode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kpEncode.encodeKey(secretKey(PLAIN_KEY), KEY_PASSWORD);
        final KeyProtector kpDecode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        kpDecode.decodeSecretKey(encodedKey, "RAW", BAD_PASSWORD);
    }

    @Test(expectedExceptions = LocalizedKeyStoreException.class)
    public void shouldFailWhenDecodeWithWrongKeyStorePassword() throws Exception {
        final KeyProtector kpEncode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kpEncode.encodeKey(secretKey(PLAIN_KEY), KEY_PASSWORD);
        final KeyProtector kpDecode = forPasswordProtectedKeyStore(BAD_PASSWORD);
        kpDecode.decodeSecretKey(encodedKey, "RAW", KEY_PASSWORD);
    }

    @Test(expectedExceptions = LocalizedKeyStoreException.class)
    public void shouldFailWhenDecodeWithMissingKeyStorePassword() throws Exception {
        final KeyProtector kpEncode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kpEncode.encodeKey(secretKey(PLAIN_KEY), KEY_PASSWORD);
        final KeyProtector kpDecode = forPasswordProtectedKeyStore(null);
        kpDecode.decodeSecretKey(encodedKey, "RAW", KEY_PASSWORD);
    }

    @Test(expectedExceptions = LocalizedKeyStoreException.class)
    public void shouldFailWhenDecodeWithMissingIndividualPassword() throws Exception {
        final KeyProtector kpEncode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kpEncode.encodeKey(secretKey(PLAIN_KEY), KEY_PASSWORD);
        final KeyProtector kpDecode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        kpDecode.decodeSecretKey(encodedKey, "RAW", null);
    }

    @Test(expectedExceptions = LocalizedKeyStoreException.class)
    public void shouldFailWhenDecodeWithMissingPasswords() throws Exception {
        final KeyProtector kpEncode = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kpEncode.encodeKey(secretKey(PLAIN_KEY), KEY_PASSWORD);
        final KeyProtector kpDecode = forPasswordProtectedKeyStore(null);
        kpDecode.decodeSecretKey(encodedKey, "RAW", null);
    }

    @Test(expectedExceptions = LocalizedKeyStoreException.class)
    public void shouldFailWhenDecodeWithMalformedEncodedKey() throws Exception {
        final KeyProtector kp = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        kp.decodeSecretKey(ByteString.valueOfUtf8("malformed encoded key"), "RAW", KEY_PASSWORD);
    }

    @Test
    public void shouldEncodeAndDecodeKeysThatNeedPadding() throws Exception {
        final KeyProtector kp = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kp.encodeKey(secretKey(PLAIN_KEY_NEEDS_PADDING), KEY_PASSWORD);
        assertThat(encodedKey).isNotEqualTo(PLAIN_KEY_NEEDS_PADDING);
        final byte[] decodedKey = kp.decodeSecretKey(encodedKey, "RAW", KEY_PASSWORD).getEncoded();
        assertThat(decodedKey).isEqualTo(PLAIN_KEY_NEEDS_PADDING.toByteArray());
    }

    @Test
    public void shouldEncodeAndDecodePrivateKeys() throws Exception {
        final KeyProtector kp = forPasswordProtectedKeyStore(KEYSTORE_PASSWORD);
        final ByteString encodedKey = kp.encodeKey(PRIVATE_KEY, KEY_PASSWORD);
        assertThat(encodedKey).isNotEqualTo(ByteString.wrap(PRIVATE_KEY.getEncoded()));
        final byte[] decodedKey = kp.decodePrivateKey(encodedKey, "RSA", KEY_PASSWORD).getEncoded();
        assertThat(decodedKey).isEqualTo(PRIVATE_KEY.getEncoded());
    }

    private void shouldEncodeAndDecodeAndBeEncrypted(final KeyProtector kp, final char[] password) throws Exception {
        final ByteString encodedKey = kp.encodeKey(secretKey(PLAIN_KEY), password);
        assertThat(encodedKey).isNotEqualTo(PLAIN_KEY);
        assertThat(encodedKeyIsEncrypted(encodedKey)).isTrue();
        assertThat(kp.decodeSecretKey(encodedKey, "RAW", password).getEncoded()).isEqualTo(PLAIN_KEY.toByteArray());
    }

    // Best effort check to ensure that the encoded content does not contain the clear text representation of the key.
    private boolean encodedKeyIsEncrypted(final ByteString encodedKey) {
        final int end = encodedKey.length() - PLAIN_KEY.length();
        for (int i = 0; i <= end; i++) {
            final ByteString subSequence = encodedKey.subSequence(i, i + PLAIN_KEY.length());
            if (subSequence.equals(PLAIN_KEY)) {
                return false;
            }
        }
        return true;
    }

    private static Key secretKey(final ByteString rawKey) {
        return new SecretKeySpec(rawKey.toByteArray(), "RAW");
    }
}
