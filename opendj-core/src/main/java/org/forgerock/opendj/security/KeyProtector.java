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

import static com.forgerock.opendj.security.KeystoreMessages.*;
import static javax.crypto.Cipher.SECRET_KEY;
import static javax.crypto.Cipher.UNWRAP_MODE;
import static javax.crypto.Cipher.WRAP_MODE;
import static org.forgerock.opendj.security.KeyStoreParameters.EXTERNAL_KEY_WRAPPING_STRATEGY;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.KeyStoreParameters.PBKDF2_ITERATIONS;
import static org.forgerock.opendj.security.KeyStoreParameters.PBKDF2_SALT_SIZE;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Options;

/**
 * Converts an {@link Key#getEncoded() encoded key} to or from its ASN.1 representation. An instance of this class must
 * be created for each encoding or decoding attempt. Keys are encoded using the following ASN.1 format:
 * <pre>
 * -- An encoded private or secret key.
 * Key ::= SEQUENCE {
 *     -- Encoding version.
 *     version              INTEGER,
 *     key                  CHOICE {
 *         -- A clear-text key which has not been wrapped.
 *         plainKey             [0] OCTET STRING,
 *
 *         -- A key which has been wrapped by the key store's default mechanism using a combination of the key
 *         -- store's global password and/or the key's individual password.
 *         keyStoreWrappedKey   [1] SEQUENCE {
 *             salt                 OCTET STRING,
 *             wrappedKey           OCTET STRING
 *         },
 *
 *         -- A key which has been optionally wrapped by the key store's default mechanism using the key's
 *         -- individual password, if provided, and then re-wrapped by an external mechanism. The octet string format
 *         -- is defined by the external mechanism.
 *         externallyWrappedKey [2] OCTET STRING
 *     }
 * }
 * </pre>
 */
final class KeyProtector {
    // ASN.1 encoding constants.
    private static final int ENCODING_VERSION_V1 = 1;
    private static final byte PLAIN_KEY = (byte) 0xA0;
    private static final byte KEYSTORE_WRAPPED_KEY = (byte) 0xA1;
    private static final byte EXTERNALLY_WRAPPED_KEY = (byte) 0xA2;
    // Crypto parameters.
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int PBKDF2_KEY_SIZE = 128;
    private static final String CIPHER_ALGORITHM = "AESWrap";
    private static final String DUMMY_KEY_ALGORITHM = "PADDED";
    /** PRNG for encoding keys. */
    private final SecureRandom prng = new SecureRandom();
    /** The key protection options. */
    private final Options options;

    KeyProtector(final Options options) {
        this.options = options;
    }

    ByteString encodeKey(final Key key, final char[] keyPassword) throws LocalizedKeyStoreException {
        final char[] keyStorePassword = options.get(GLOBAL_PASSWORD).newInstance();
        final char[] concatenatedPasswords = concatenate(keyStorePassword, keyPassword);
        final ByteStringBuilder builder = new ByteStringBuilder();
        try (final ASN1Writer asn1Writer = ASN1.getWriter(builder)) {
            asn1Writer.writeStartSequence();
            asn1Writer.writeInteger(ENCODING_VERSION_V1);
            final ExternalKeyWrappingStrategy strategy = options.get(EXTERNAL_KEY_WRAPPING_STRATEGY);
            if (strategy == null) {
                encodePlainOrWrappedKey(key, concatenatedPasswords, asn1Writer);
            } else {
                final ByteStringBuilder externalBuilder = new ByteStringBuilder();
                try (final ASN1Writer externalAsn1Writer = ASN1.getWriter(externalBuilder)) {
                    encodePlainOrWrappedKey(key, concatenatedPasswords, externalAsn1Writer);
                }
                final ByteSequence externallyWrappedKey = strategy.wrapKey(externalBuilder.toByteString());
                asn1Writer.writeOctetString(EXTERNALLY_WRAPPED_KEY, externallyWrappedKey);
            }
            asn1Writer.writeEndSequence();
        } catch (final IOException e) {
            // IO exceptions should not occur during encoding because we are writing to a byte array.
            throw new IllegalStateException(e);
        } finally {
            destroyCharArray(concatenatedPasswords);
            destroyCharArray(keyStorePassword);
        }
        return builder.toByteString();
    }

    private void encodePlainOrWrappedKey(final Key key, final char[] concatenatedPasswords, final ASN1Writer asn1Writer)
            throws IOException, LocalizedKeyStoreException {
        if (concatenatedPasswords == null) {
            asn1Writer.writeOctetString(PLAIN_KEY, key.getEncoded());
        } else {
            asn1Writer.writeStartSequence(KEYSTORE_WRAPPED_KEY);

            final byte[] salt = new byte[options.get(PBKDF2_SALT_SIZE)];
            prng.nextBytes(salt);
            asn1Writer.writeOctetString(salt);

            final Integer iterations = options.get(PBKDF2_ITERATIONS);
            final SecretKey aesKey = createAESSecretKey(concatenatedPasswords, salt, iterations);
            final Cipher cipher = getCipher(WRAP_MODE, aesKey);
            try {
                final byte[] wrappedKey = cipher.wrap(pad(key));
                asn1Writer.writeOctetString(wrappedKey);
            } catch (final IllegalBlockSizeException | InvalidKeyException e) {
                throw new IllegalStateException(e); // Should not happen because padding is ok.
            }

            asn1Writer.writeEndSequence();
        }
    }

    Key decodeSecretKey(final ByteSequence encodedKey, final String algorithm, final char[] keyPassword)
            throws LocalizedKeyStoreException {
        return decodeKey(encodedKey, algorithm, keyPassword, false);
    }

    Key decodePrivateKey(final ByteSequence encodedKey, final String algorithm, final char[] keyPassword)
            throws LocalizedKeyStoreException {
        return decodeKey(encodedKey, algorithm, keyPassword, true);
    }

    private Key decodeKey(final ByteSequence encodedKey, final String algorithm, final char[] keyPassword,
                          final boolean isPrivateKey) throws LocalizedKeyStoreException {
        try (final ASN1Reader asn1Reader = ASN1.getReader(encodedKey)) {
            asn1Reader.readStartSequence();
            final int version = (int) asn1Reader.readInteger();
            final Key key;
            switch (version) {
            case ENCODING_VERSION_V1:
                key = decodeKeyV1(asn1Reader, algorithm, keyPassword, isPrivateKey);
                break;
            default:
                throw new LocalizedKeyStoreException(KEYSTORE_DECODE_UNSUPPORTED_VERSION.get(version));
            }
            asn1Reader.readEndSequence();
            return key;
        } catch (final IOException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_DECODE_MALFORMED.get(), e);
        }
    }

    private Key decodeKeyV1(final ASN1Reader asn1Reader, final String algorithm, final char[] keyPassword,
                            final boolean isPrivateKey) throws IOException, LocalizedKeyStoreException {
        switch (asn1Reader.peekType()) {
        case PLAIN_KEY:
            final byte[] plainKey = asn1Reader.readOctetString(PLAIN_KEY).toByteArray();
            return newKeyFromBytes(plainKey, algorithm, isPrivateKey);
        case KEYSTORE_WRAPPED_KEY:
            final char[] keyStorePassword = options.get(GLOBAL_PASSWORD).newInstance();
            final char[] concatenatedPasswords = concatenate(keyStorePassword, keyPassword);
            if (concatenatedPasswords == null) {
                throw new LocalizedKeyStoreException(KEYSTORE_DECODE_KEY_MISSING_PWD.get());
            }
            asn1Reader.readStartSequence(KEYSTORE_WRAPPED_KEY);
            try {
                final byte[] salt = asn1Reader.readOctetString().toByteArray();
                final byte[] wrappedKey = asn1Reader.readOctetString().toByteArray();
                final Integer iterations = options.get(PBKDF2_ITERATIONS);
                final SecretKey aesKey = createAESSecretKey(concatenatedPasswords, salt, iterations);
                final Cipher cipher = getCipher(UNWRAP_MODE, aesKey);
                final Key paddedKey = cipher.unwrap(wrappedKey, DUMMY_KEY_ALGORITHM, SECRET_KEY);
                return unpad(paddedKey, algorithm, isPrivateKey);
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalStateException(e); // Should not happen because it's a pseudo secret key.
            } catch (final InvalidKeyException e) {
                throw new LocalizedKeyStoreException(KEYSTORE_DECODE_KEYSTORE_DECRYPT_FAILURE.get(), e);
            } finally {
                destroyCharArray(concatenatedPasswords);
                destroyCharArray(keyStorePassword);
                asn1Reader.readEndSequence();
            }
        case EXTERNALLY_WRAPPED_KEY:
            final ExternalKeyWrappingStrategy strategy = options.get(EXTERNAL_KEY_WRAPPING_STRATEGY);
            if (strategy == null) {
                throw new LocalizedKeyStoreException(KEYSTORE_DECODE_KEY_MISSING_KEYSTORE_EXT.get());
            }
            final ByteString externallyWrappedKey = asn1Reader.readOctetString(EXTERNALLY_WRAPPED_KEY);
            try (final ASN1Reader externalAsn1Reader = ASN1.getReader(strategy.unwrapKey(externallyWrappedKey))) {
                return decodeKeyV1(externalAsn1Reader, algorithm, keyPassword, isPrivateKey);
            }
        default:
            throw new LocalizedKeyStoreException(KEYSTORE_DECODE_MALFORMED.get());
        }
    }

    private static char[] concatenate(final char[] keyStorePassword, final char[] keyPassword) {
        if (keyStorePassword == null && keyPassword == null) {
            return null;
        } else if (keyStorePassword == null) {
            return keyPassword.clone();
        } else if (keyPassword == null) {
            return keyStorePassword.clone();
        } else {
            final char[] concat = new char[keyStorePassword.length + keyPassword.length];
            System.arraycopy(keyStorePassword, 0, concat, 0, keyStorePassword.length);
            System.arraycopy(keyPassword, 0, concat, keyStorePassword.length, keyPassword.length);
            return concat;
        }
    }

    private static Cipher getCipher(final int cipherMode, final SecretKey aesKey) throws LocalizedKeyStoreException {
        try {
            final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(cipherMode, aesKey);
            return cipher;
        } catch (final NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_UNSUPPORTED_CIPHER.get(CIPHER_ALGORITHM), e);
        } catch (final InvalidKeyException e) {
            // Should not happen.
            throw new IllegalStateException("key is incompatible with the cipher", e);
        }
    }

    private static SecretKey createAESSecretKey(final char[] password, final byte[] salt, final Integer iterations)
            throws LocalizedKeyStoreException {
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            final PBEKeySpec pbeKeySpec = new PBEKeySpec(password, salt, iterations, PBKDF2_KEY_SIZE);
            try {
                final SecretKey pbeKey = factory.generateSecret(pbeKeySpec);
                return new SecretKeySpec(pbeKey.getEncoded(), "AES");
            } finally {
                pbeKeySpec.clearPassword();
            }
        } catch (final NoSuchAlgorithmException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_UNSUPPORTED_KF.get(PBKDF2_ALGORITHM), e);
        } catch (final InvalidKeySpecException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_UNSUPPORTED_KF_ARGS.get(PBKDF2_ALGORITHM,
                                                                                  iterations,
                                                                                  PBKDF2_KEY_SIZE), e);
        }
    }

    /**
     * AESWrap implementation only supports encoded keys that are multiple of 8 bytes. This method always adds
     * padding, even if it's not needed, in order to avoid ambiguity.
     */
    private static Key pad(final Key key) {
        final byte[] keyBytes = key.getEncoded();
        final int keySize = keyBytes.length;
        final int paddingSize = 8 - (keySize % 8);
        final byte[] paddedKeyBytes = Arrays.copyOf(keyBytes, keySize + paddingSize);
        for (int i = 0; i < paddingSize; i++) {
            paddedKeyBytes[keySize + i] = (byte) (i + 1);
        }
        return new SecretKeySpec(paddedKeyBytes, DUMMY_KEY_ALGORITHM);
    }

    private static Key unpad(final Key paddedKey, final String algorithm, final boolean isPrivateKey)
            throws LocalizedKeyStoreException {
        final byte[] paddedKeyBytes = paddedKey.getEncoded();
        final int paddedKeySize = paddedKeyBytes.length;
        if (paddedKeySize < 8) {
            throw new LocalizedKeyStoreException(KEYSTORE_DECODE_BAD_PADDING.get());
        }
        final byte paddingSize = paddedKeyBytes[paddedKeySize - 1];
        if (paddingSize < 1 || paddingSize > 8) {
            throw new LocalizedKeyStoreException(KEYSTORE_DECODE_BAD_PADDING.get());
        }
        final int keySize = paddedKeySize - paddingSize;
        for (int i = 0; i < paddingSize; i++) {
            if (paddedKeyBytes[keySize + i] != (i + 1)) {
                throw new LocalizedKeyStoreException(KEYSTORE_DECODE_BAD_PADDING.get());
            }
        }
        final byte[] keyBytes = Arrays.copyOf(paddedKeyBytes, keySize);
        return newKeyFromBytes(keyBytes, algorithm, isPrivateKey);
    }

    private static Key newKeyFromBytes(final byte[] plainKey, final String algorithm, final boolean isPrivateKey)
            throws LocalizedKeyStoreException {
        if (isPrivateKey) {
            try {
                final KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(plainKey));
            } catch (final InvalidKeySpecException | NoSuchAlgorithmException e) {
                throw new LocalizedKeyStoreException(KEYSTORE_UNSUPPORTED_KF.get(algorithm), e);
            }
        } else {
            return new SecretKeySpec(plainKey, algorithm);
        }
    }

    private static void destroyCharArray(final char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, ' ');
        }
    }
}
