/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import org.forgerock.util.Reject;

/**
 * This class contains methods for creating common types of key manager.
 */
public final class KeyManagers {
    /**
     * This class implements an X.509 key manager that will be used to wrap an
     * existing key manager and makes it possible to configure which
     * certificate(s) should be used for client and/or server operations. The
     * certificate selection will be based on the alias (also called the
     * nickname) of the certificate.
     */
    private static final class SelectCertificate extends X509ExtendedKeyManager {
        private final String alias;
        private final X509KeyManager keyManager;

        private SelectCertificate(final X509KeyManager keyManager, final String alias) {
            this.keyManager = keyManager;
            this.alias = alias;
        }

        /** {@inheritDoc} */
        public String chooseClientAlias(final String[] keyType, final Principal[] issuers,
                final Socket socket) {
            for (final String type : keyType) {
                final String[] clientAliases = keyManager.getClientAliases(type, issuers);
                if (clientAliases != null) {
                    for (final String clientAlias : clientAliases) {
                        if (clientAlias.equals(alias)) {
                            return alias;
                        }
                    }
                }
            }

            return null;
        }

        /** {@inheritDoc} */
        @Override
        public String chooseEngineClientAlias(final String[] keyType, final Principal[] issuers,
                final SSLEngine engine) {
            for (final String type : keyType) {
                final String[] clientAliases = keyManager.getClientAliases(type, issuers);
                if (clientAliases != null) {
                    for (final String clientAlias : clientAliases) {
                        if (clientAlias.equals(alias)) {
                            return alias;
                        }
                    }
                }
            }

            return null;
        }

        /** {@inheritDoc} */
        @Override
        public String chooseEngineServerAlias(final String keyType, final Principal[] issuers,
                final SSLEngine engine) {
            final String[] serverAliases = keyManager.getServerAliases(keyType, issuers);
            if (serverAliases != null) {
                for (final String serverAlias : serverAliases) {
                    if (serverAlias.equalsIgnoreCase(alias)) {
                        return serverAlias;
                    }
                }
            }

            return null;
        }

        /** {@inheritDoc} */
        public String chooseServerAlias(final String keyType, final Principal[] issuers,
                final Socket socket) {
            final String[] serverAliases = keyManager.getServerAliases(keyType, issuers);
            if (serverAliases != null) {
                for (final String serverAlias : serverAliases) {
                    if (serverAlias.equals(alias)) {
                        return alias;
                    }
                }
            }

            return null;
        }

        /** {@inheritDoc} */
        public X509Certificate[] getCertificateChain(final String alias) {
            return keyManager.getCertificateChain(alias);
        }

        /** {@inheritDoc} */
        public String[] getClientAliases(final String keyType, final Principal[] issuers) {
            return keyManager.getClientAliases(keyType, issuers);
        }

        /** {@inheritDoc} */
        public PrivateKey getPrivateKey(final String alias) {
            return keyManager.getPrivateKey(alias);
        }

        /** {@inheritDoc} */
        public String[] getServerAliases(final String keyType, final Principal[] issuers) {
            return keyManager.getServerAliases(keyType, issuers);
        }
    }

    /**
     * Creates a new {@code X509KeyManager} which will use the named key store
     * file for retrieving certificates. It will use the default key store
     * format for the JVM (e.g. {@code JKS}) and will not use a password to open
     * the key store.
     *
     * @param file
     *            The key store file name.
     * @return A new {@code X509KeyManager} which will use the named key store
     *         file for retrieving certificates.
     * @throws GeneralSecurityException
     *             If the key store could not be loaded, perhaps due to
     *             incorrect format, or missing algorithms.
     * @throws IOException
     *             If the key store file could not be found or could not be
     *             read.
     * @throws NullPointerException
     *             If {@code file} was {@code null}.
     */
    public static X509KeyManager useKeyStoreFile(final String file)
            throws GeneralSecurityException, IOException {
        return useKeyStoreFile(file, null, null);
    }

    /**
     * Creates a new {@code X509KeyManager} which will use the named key store
     * file for retrieving certificates. It will use the provided key store
     * format and password.
     *
     * @param file
     *            The key store file name.
     * @param password
     *            The key store password, which may be {@code null}.
     * @param format
     *            The key store format, which may be {@code null} to indicate
     *            that the default key store format for the JVM (e.g.
     *            {@code JKS}) should be used.
     * @return A new {@code X509KeyManager} which will use the named key store
     *         file for retrieving certificates.
     * @throws GeneralSecurityException
     *             If the key store could not be loaded, perhaps due to
     *             incorrect format, or missing algorithms.
     * @throws IOException
     *             If the key store file could not be found or could not be
     *             read.
     * @throws NullPointerException
     *             If {@code file} was {@code null}.
     */
    public static X509KeyManager useKeyStoreFile(final String file, final char[] password,
            final String format) throws GeneralSecurityException, IOException {
        Reject.ifNull(file);

        final File keyStoreFile = new File(file);
        final String keyStoreFormat = format != null ? format : KeyStore.getDefaultType();

        final KeyStore keyStore = KeyStore.getInstance(keyStoreFormat);

        FileInputStream fos = null;
        try {
            fos = new FileInputStream(keyStoreFile);
            keyStore.load(fos, password);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (final IOException ignored) {
                    // Ignore.
                }
            }
        }

        final KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        X509KeyManager x509km = null;
        for (final KeyManager km : kmf.getKeyManagers()) {
            if (km instanceof X509KeyManager) {
                x509km = (X509KeyManager) km;
                break;
            }
        }

        if (x509km == null) {
            throw new NoSuchAlgorithmException();
        }

        return x509km;
    }

    /**
     * Creates a new {@code X509KeyManager} which will use a PKCS#11 token for
     * retrieving certificates.
     *
     * @param password
     *            The password to use for accessing the PKCS#11 token, which may
     *            be {@code null} if no password is required.
     * @return A new {@code X509KeyManager} which will use a PKCS#11 token for
     *         retrieving certificates.
     * @throws GeneralSecurityException
     *             If the PKCS#11 token could not be accessed, perhaps due to
     *             incorrect password, or missing algorithms.
     * @throws IOException
     *             If the PKCS#11 token could not be found or could not be read.
     */
    public static X509KeyManager usePKCS11Token(final char[] password)
            throws GeneralSecurityException, IOException {
        final KeyStore keyStore = KeyStore.getInstance("PKCS11");
        keyStore.load(null, password);
        final KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        X509KeyManager x509km = null;
        for (final KeyManager km : kmf.getKeyManagers()) {
            if (km instanceof X509KeyManager) {
                x509km = (X509KeyManager) km;
                break;
            }
        }

        if (x509km == null) {
            throw new NoSuchAlgorithmException();
        }

        return x509km;
    }

    /**
     * Returns a new {@code X509KeyManager} which selects the named certificate
     * from the provided {@code X509KeyManager}.
     *
     * @param alias
     *            The nickname of the certificate that should be selected for
     *            operations involving this key manager.
     * @param keyManager
     *            The key manager to be filtered.
     * @return The filtered key manager.
     * @throws NullPointerException
     *             If {@code keyManager} or {@code alias} was {@code null}.
     */
    public static X509KeyManager useSingleCertificate(final String alias,
            final X509KeyManager keyManager) {
        Reject.ifNull(alias, keyManager);
        return new SelectCertificate(keyManager, alias);
    }

    /** Prevent insantiation. */
    private KeyManagers() {
        // Nothing to do.
    }

}
