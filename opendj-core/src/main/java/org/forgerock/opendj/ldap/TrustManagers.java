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
 *      Portions copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

/**
 * This class contains methods for creating common types of trust manager.
 */
public final class TrustManagers {

    /**
     * An X509TrustManager which rejects certificate chains whose subject DN
     * does not match a specified host name.
     */
    private static final class CheckHostName implements X509TrustManager {

        private final X509TrustManager trustManager;

        private final String hostNamePattern;

        private CheckHostName(final X509TrustManager trustManager, final String hostNamePattern) {
            this.trustManager = trustManager;
            this.hostNamePattern = hostNamePattern;
        }

        /** {@inheritDoc} */
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyHostName(chain);
            trustManager.checkClientTrusted(chain, authType);
        }

        /** {@inheritDoc} */
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyHostName(chain);
            trustManager.checkServerTrusted(chain, authType);
        }

        /** {@inheritDoc} */
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        /**
         * Checks whether a host name matches the provided pattern. It accepts
         * the use of wildcards in the pattern, e.g. {@code *.example.com}.
         *
         * @param hostName
         *            The host name.
         * @param pattern
         *            The host name pattern, which may contain wild cards.
         * @return {@code true} if the host name matched the pattern, otherwise
         *         {@code false}.
         */
        private boolean hostNameMatchesPattern(final String hostName, final String pattern) {
            final String[] nameElements = hostName.split("\\.");
            final String[] patternElements = pattern.split("\\.");

            boolean hostMatch = nameElements.length == patternElements.length;
            for (int i = 0; i < nameElements.length && hostMatch; i++) {
                final String ne = nameElements[i];
                final String pe = patternElements[i];
                if (!pe.equals("*")) {
                    hostMatch = ne.equalsIgnoreCase(pe);
                }
            }
            return hostMatch;
        }

        private void verifyHostName(final X509Certificate[] chain) {
            try {
                // TODO: NPE if root DN.
                final DN dn =
                        DN.valueOf(chain[0].getSubjectX500Principal().getName(), Schema
                                .getCoreSchema());
                final String value =
                        dn.iterator().next().iterator().next().getAttributeValue().toString();
                if (!hostNameMatchesPattern(value, hostNamePattern)) {
                    throw new CertificateException(
                            "The host name contained in the certificate chain subject DN \'"
                                    + chain[0].getSubjectX500Principal()
                                    + "' does not match the host name \'" + hostNamePattern + "'");
                }
            } catch (final Throwable t) {
                LOG.log(Level.WARNING, "Error parsing subject dn: "
                        + chain[0].getSubjectX500Principal(), t);
            }
        }
    }

    /**
     * An X509TrustManager which rejects certificates which have expired or are
     * not yet valid.
     */
    private static final class CheckValidityDates implements X509TrustManager {

        private final X509TrustManager trustManager;

        private CheckValidityDates(final X509TrustManager trustManager) {
            this.trustManager = trustManager;
        }

        /** {@inheritDoc} */
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyExpiration(chain);
            trustManager.checkClientTrusted(chain, authType);
        }

        /** {@inheritDoc} */
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyExpiration(chain);
            trustManager.checkServerTrusted(chain, authType);
        }

        /** {@inheritDoc} */
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        private void verifyExpiration(final X509Certificate[] chain) throws CertificateException {
            final Date currentDate = new Date();
            for (final X509Certificate c : chain) {
                try {
                    c.checkValidity(currentDate);
                } catch (final CertificateExpiredException e) {
                    LOG.log(Level.WARNING, "Refusing to trust security" + " certificate \""
                            + c.getSubjectDN().getName() + "\" because it" + " expired on "
                            + String.valueOf(c.getNotAfter()));

                    throw e;
                } catch (final CertificateNotYetValidException e) {
                    LOG.log(Level.WARNING, "Refusing to trust security" + " certificate \""
                            + c.getSubjectDN().getName() + "\" because it" + " is not valid until "
                            + String.valueOf(c.getNotBefore()));

                    throw e;
                }
            }
        }
    }

    /**
     * An X509TrustManager which does not trust any certificates.
     */
    private static final class DistrustAll implements X509TrustManager {
        /** Single instance. */
        private static final DistrustAll INSTANCE = new DistrustAll();

        /** Prevent instantiation. */
        private DistrustAll() {
            // Nothing to do.
        }

        /** {@inheritDoc} */
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            throw new CertificateException();
        }

        /** {@inheritDoc} */
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            throw new CertificateException();
        }

        /** {@inheritDoc} */
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /**
     * An X509TrustManager which trusts all certificates.
     */
    private static final class TrustAll implements X509TrustManager {

        /** Single instance. */
        private static final TrustAll INSTANCE = new TrustAll();

        /** Prevent instantiation. */
        private TrustAll() {
            // Nothing to do.
        }

        /** {@inheritDoc} */
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
        }

        /** {@inheritDoc} */
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
        }

        /** {@inheritDoc} */
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static final Logger LOG = Logger.getLogger(TrustManagers.class.getName());

    /**
     * Wraps the provided {@code X509TrustManager} by adding additional
     * validation which rejects certificate chains whose subject DN does not
     * match the specified host name pattern. The pattern may contain
     * wild-cards, for example {@code *.example.com}.
     *
     * @param hostNamePattern
     *            A host name pattern which the RDN value contained in
     *            certificate subject DNs must match.
     * @param trustManager
     *            The trust manager to be wrapped.
     * @return The wrapped trust manager.
     * @throws NullPointerException
     *             If {@code trustManager} or {@code hostNamePattern} was
     *             {@code null}.
     */
    public static X509TrustManager checkHostName(final String hostNamePattern,
            final X509TrustManager trustManager) {
        Reject.ifNull(trustManager, hostNamePattern);
        return new CheckHostName(trustManager, hostNamePattern);
    }

    /**
     * Creates a new {@code X509TrustManager} which will use the named trust
     * store file to determine whether to trust a certificate. It will use the
     * default trust store format for the JVM (e.g. {@code JKS}) and will not
     * use a password to open the trust store.
     *
     * @param file
     *            The trust store file name.
     * @return A new {@code X509TrustManager} which will use the named trust
     *         store file to determine whether to trust a certificate.
     * @throws GeneralSecurityException
     *             If the trust store could not be loaded, perhaps due to
     *             incorrect format, or missing algorithms.
     * @throws IOException
     *             If the trust store file could not be found or could not be
     *             read.
     * @throws NullPointerException
     *             If {@code file} was {@code null}.
     */
    public static X509TrustManager checkUsingTrustStore(final String file)
            throws GeneralSecurityException, IOException {
        return checkUsingTrustStore(file, null, null);
    }

    /**
     * Creates a new {@code X509TrustManager} which will use the named trust
     * store file to determine whether to trust a certificate. It will use the
     * provided trust store format and password.
     *
     * @param file
     *            The trust store file name.
     * @param password
     *            The trust store password, which may be {@code null}.
     * @param format
     *            The trust store format, which may be {@code null} to indicate
     *            that the default trust store format for the JVM (e.g.
     *            {@code JKS}) should be used.
     * @return A new {@code X509TrustManager} which will use the named trust
     *         store file to determine whether to trust a certificate.
     * @throws GeneralSecurityException
     *             If the trust store could not be loaded, perhaps due to
     *             incorrect format, or missing algorithms.
     * @throws IOException
     *             If the trust store file could not be found or could not be
     *             read.
     * @throws NullPointerException
     *             If {@code file} was {@code null}.
     */
    public static X509TrustManager checkUsingTrustStore(final String file, final char[] password,
            final String format) throws GeneralSecurityException, IOException {
        Reject.ifNull(file);

        final File trustStoreFile = new File(file);
        final String trustStoreFormat = format != null ? format : KeyStore.getDefaultType();

        final KeyStore keyStore = KeyStore.getInstance(trustStoreFormat);

        FileInputStream fos = null;
        try {
            fos = new FileInputStream(trustStoreFile);
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

        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        X509TrustManager x509tm = null;
        for (final TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                x509tm = (X509TrustManager) tm;
                break;
            }
        }

        if (x509tm == null) {
            throw new NoSuchAlgorithmException();
        }

        return x509tm;
    }

    /**
     * Wraps the provided {@code X509TrustManager} by adding additional
     * validation which rejects certificate chains containing certificates which
     * have expired or are not yet valid.
     *
     * @param trustManager
     *            The trust manager to be wrapped.
     * @return The wrapped trust manager.
     * @throws NullPointerException
     *             If {@code trustManager} was {@code null}.
     */
    public static X509TrustManager checkValidityDates(final X509TrustManager trustManager) {
        Reject.ifNull(trustManager);
        return new CheckValidityDates(trustManager);
    }

    /**
     * Returns an {@code X509TrustManager} which does not trust any
     * certificates.
     *
     * @return An {@code X509TrustManager} which does not trust any
     *         certificates.
     */
    public static X509TrustManager distrustAll() {
        return DistrustAll.INSTANCE;
    }

    /**
     * Returns an {@code X509TrustManager} which trusts all certificates.
     *
     * @return An {@code X509TrustManager} which trusts all certificates.
     */
    public static X509TrustManager trustAll() {
        return TrustAll.INSTANCE;
    }

    /** Prevent insantiation. */
    private TrustManagers() {
        // Nothing to do.
    }

}
