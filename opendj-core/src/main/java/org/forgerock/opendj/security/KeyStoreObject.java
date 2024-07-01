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

import static com.forgerock.opendj.security.KeystoreMessages.KEYSTORE_ENTRY_MALFORMED;
import static com.forgerock.opendj.security.KeystoreMessages.KEYSTORE_UNRECOGNIZED_OBJECT_CLASS;
import static org.forgerock.opendj.ldap.Functions.byteStringToCertificate;
import static org.forgerock.opendj.security.OpenDJProviderSchema.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;

/** An in memory representation of a LDAP key store object. */
public final class KeyStoreObject {
    private interface Impl {
        void addAttributes(Entry entry);

        Certificate[] getCertificateChain();

        Certificate getCertificate();

        Key toKey(KeyProtector protector, char[] keyPassword) throws GeneralSecurityException;
    }

    private static final class TrustedCertificateImpl implements Impl {
        private final Certificate trustedCertificate;

        private TrustedCertificateImpl(Certificate trustedCertificate) {
            this.trustedCertificate = trustedCertificate;
        }

        @Override
        public void addAttributes(final Entry entry) {
            entry.addAttribute(ATTR_OBJECT_CLASS, "top", OC_KEY_STORE_OBJECT, OC_TRUSTED_CERTIFICATE);
            entry.addAttribute(ATTR_CERTIFICATE_BINARY, encodeCertificate(trustedCertificate));
        }

        @Override
        public Certificate[] getCertificateChain() {
            return null;
        }

        @Override
        public Certificate getCertificate() {
            return trustedCertificate;
        }

        @Override
        public Key toKey(final KeyProtector protector, final char[] keyPassword) {
            return null;
        }
    }

    private static final class PrivateKeyImpl implements Impl {
        private final String algorithm;
        private final ByteString protectedKey;
        private final Certificate[] certificateChain;

        private PrivateKeyImpl(String algorithm, ByteString protectedKey, Certificate[] chain) {
            this.algorithm = algorithm;
            this.protectedKey = protectedKey;
            this.certificateChain = chain.clone();
        }

        @Override
        public void addAttributes(final Entry entry) {
            entry.addAttribute(ATTR_OBJECT_CLASS, "top", OC_KEY_STORE_OBJECT, OC_PRIVATE_KEY);
            entry.addAttribute(ATTR_KEY_ALGORITHM, algorithm);
            entry.addAttribute(ATTR_KEY, protectedKey);
            // The KeyStore method "getCertificateAlias" is best implemented using an LDAP search whose filter is a
            // certificateExactMatch assertion against an attribute whose value conforms to the LDAP "certificate"
            // syntax. To facilitate this we split out the first certificate from the rest of the certificate chain.
            entry.addAttribute(ATTR_CERTIFICATE_BINARY, encodeCertificate(certificateChain[0]));
            if (certificateChain.length > 1) {
                entry.addAttribute(ATTR_CERTIFICATE_CHAIN, encodeCertificateChain());
            }
        }

        // Encode all certificates except the first which is stored separately.
        private ByteString encodeCertificateChain() {
            final ByteStringBuilder builder = new ByteStringBuilder();
            final ASN1Writer writer = ASN1.getWriter(builder);
            try {
                writer.writeStartSequence();
                for (int i = 1; i < certificateChain.length; i++) {
                    writer.writeOctetString(encodeCertificate(certificateChain[i]));
                }
                writer.writeEndSequence();
            } catch (IOException e) {
                // Should not happen.
                throw new RuntimeException(e);
            }
            return builder.toByteString();
        }

        @Override
        public Certificate[] getCertificateChain() {
            return certificateChain.clone();
        }

        @Override
        public Certificate getCertificate() {
            return certificateChain.length > 0 ? certificateChain[0] : null;
        }

        @Override
        public Key toKey(final KeyProtector protector, final char[] keyPassword) throws GeneralSecurityException {
            return protector.decodePrivateKey(protectedKey, algorithm, keyPassword);
        }
    }

    private static final class SecretKeyImpl implements Impl {
        private final String algorithm;
        private final ByteString protectedKey;

        private SecretKeyImpl(String algorithm, ByteString protectedKey) {
            this.algorithm = algorithm;
            this.protectedKey = protectedKey;
        }

        @Override
        public void addAttributes(final Entry entry) {
            entry.addAttribute(ATTR_OBJECT_CLASS, "top", OC_KEY_STORE_OBJECT, OC_SECRET_KEY);
            entry.addAttribute(ATTR_KEY_ALGORITHM, algorithm);
            entry.addAttribute(ATTR_KEY, protectedKey);
        }

        @Override
        public Certificate[] getCertificateChain() {
            return null;
        }

        @Override
        public Certificate getCertificate() {
            return null;
        }

        @Override
        public Key toKey(final KeyProtector protector, final char[] keyPassword) throws GeneralSecurityException {
            return protector.decodeSecretKey(protectedKey, algorithm, keyPassword);
        }
    }

    static KeyStoreObject newTrustedCertificateObject(String alias, Certificate certificate) {
        return new KeyStoreObject(alias, new Date(), new TrustedCertificateImpl(certificate));
    }

    static KeyStoreObject newKeyObject(String alias, Key key, Certificate[] chain, KeyProtector protector,
                                       char[] keyPassword) throws LocalizedKeyStoreException {
        final ByteString protectedKey = protector.encodeKey(key, keyPassword);
        final Impl impl = key instanceof PrivateKey
                ? new PrivateKeyImpl(key.getAlgorithm(), protectedKey, chain)
                : new SecretKeyImpl(key.getAlgorithm(), protectedKey);
        return new KeyStoreObject(alias, new Date(), impl);
    }

    static KeyStoreObject valueOf(final Entry ldapEntry) throws LocalizedKeyStoreException {
        try {
            final String alias = ldapEntry.parseAttribute(ATTR_ALIAS).requireValue().asString();

            GeneralizedTime timeStamp = ldapEntry.parseAttribute(ATTR_MODIFY_TIME_STAMP).asGeneralizedTime();
            if (timeStamp == null) {
                timeStamp = ldapEntry.parseAttribute(ATTR_CREATE_TIME_STAMP).asGeneralizedTime();
            }
            final Date creationDate = timeStamp != null ? timeStamp.toDate() : new Date();

            final Impl impl;
            if (ldapEntry.containsAttribute(ATTR_OBJECT_CLASS, OC_TRUSTED_CERTIFICATE)) {
                impl = valueOfTrustedCertificate(ldapEntry);
            } else if (ldapEntry.containsAttribute(ATTR_OBJECT_CLASS, OC_PRIVATE_KEY)) {
                impl = valueOfPrivateKey(ldapEntry);
            } else if (ldapEntry.containsAttribute(ATTR_OBJECT_CLASS, OC_SECRET_KEY)) {
                impl = valueOfSecretKey(ldapEntry);
            } else {
                throw new LocalizedKeyStoreException(KEYSTORE_UNRECOGNIZED_OBJECT_CLASS.get(ldapEntry.getName()));
            }
            return new KeyStoreObject(alias, creationDate, impl);
        } catch (LocalizedIllegalArgumentException | IOException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_ENTRY_MALFORMED.get(ldapEntry.getName()), e);
        }
    }

    private static Impl valueOfSecretKey(final Entry ldapEntry) {
        final String algorithm = ldapEntry.parseAttribute(ATTR_KEY_ALGORITHM).requireValue().asString();
        final ByteString protectedKey = ldapEntry.parseAttribute(ATTR_KEY).requireValue().asByteString();
        return new SecretKeyImpl(algorithm, protectedKey);
    }

    private static Impl valueOfPrivateKey(final Entry ldapEntry) throws IOException {
        final String algorithm = ldapEntry.parseAttribute(ATTR_KEY_ALGORITHM).requireValue().asString();
        final ByteString protectedKey = ldapEntry.parseAttribute(ATTR_KEY).requireValue().asByteString();
        final List<Certificate> certificateChainList = new ArrayList<>();
        final X509Certificate publicKeyCertificate =
                ldapEntry.parseAttribute(ATTR_CERTIFICATE_BINARY).requireValue().asCertificate();
        certificateChainList.add(publicKeyCertificate);
        final ByteString encodedCertificateChain = ldapEntry.parseAttribute(ATTR_CERTIFICATE_CHAIN).asByteString();
        if (encodedCertificateChain != null) {
            final ASN1Reader reader = ASN1.getReader(encodedCertificateChain);
            reader.readStartSequence();
            while (reader.hasNextElement()) {
                final ByteString certificate = reader.readOctetString();
                certificateChainList.add(byteStringToCertificate().apply(certificate));
            }
            reader.readEndSequence();
        }
        final Certificate[] certificateChain = certificateChainList.toArray(new Certificate[0]);
        return new PrivateKeyImpl(algorithm, protectedKey, certificateChain);
    }

    private static Impl valueOfTrustedCertificate(final Entry ldapEntry) {
        final X509Certificate trustedCertificate =
                ldapEntry.parseAttribute(ATTR_CERTIFICATE_BINARY).requireValue().asCertificate();
        return new TrustedCertificateImpl(trustedCertificate);
    }

    private static ByteString encodeCertificate(final Certificate certificate) {
        try {
            return ByteString.wrap(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            // Should not happen.
            throw new RuntimeException(e);
        }
    }

    private final String alias;
    private final Date creationDate;
    private final Impl impl;

    private KeyStoreObject(final String alias, final Date creationDate, final Impl impl) {
        this.alias = alias;
        this.creationDate = creationDate;
        this.impl = impl;
    }

    Date getCreationDate() {
        return creationDate;
    }

    /**
     * Returns the alias associated with this key store object.
     *
     * @return The alias associated with this key store object.
     */
    public String getAlias() {
        return alias;
    }

    Certificate[] getCertificateChain() {
        return impl.getCertificateChain();
    }

    boolean isTrustedCertificate() {
        return impl instanceof TrustedCertificateImpl;
    }

    Entry toLDAPEntry(final DN baseDN) {
        final LinkedHashMapEntry entry = new LinkedHashMapEntry(dnOf(baseDN, alias));
        entry.addAttribute(ATTR_ALIAS, alias);
        impl.addAttributes(entry);
        return entry;
    }

    Certificate getCertificate() {
        return impl.getCertificate();
    }

    Key getKey(final KeyProtector protector, final char[] keyPassword)
            throws NoSuchAlgorithmException, UnrecoverableKeyException {
        try {
            return impl.toKey(protector, keyPassword);
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new UnrecoverableKeyException(e.getMessage());
        }
    }

    static DN dnOf(DN baseDN, String alias) {
        return baseDN.child(ATTR_ALIAS, alias);
    }
}
