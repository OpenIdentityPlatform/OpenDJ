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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_CERT_NO_MATCH_IP;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_CERT_NO_MATCH_DNS;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_CERT_NO_MATCH_ALLOTHERS;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_CERT_NO_MATCH_SUBJECT;


/** This class contains methods for creating common types of trust manager. */
public final class TrustManagers {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * An X509TrustManager which rejects certificate chains whose subject alternative names do not match the specified
     * host name or IP address. The check may fall back to checking a hostname in the left-most CN of the certificate
     * subject for backwards compatibility.
     */
    private static final class CheckHostName implements X509TrustManager {

        private final X509TrustManager trustManager;

        private final String hostName;

        private CheckHostName(final X509TrustManager trustManager, final String hostName) {
            this.trustManager = trustManager;
            this.hostName = hostName;
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyHostName(chain);
            trustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyHostName(chain);
            trustManager.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        /**
         * Look in the SubjectAlternativeName for DNS names (wildcards are allowed) and IP addresses, and potentially
         * fall back to checking CN in the subjectDN.
         * <p>
         * If DNS names and IP addresses do not match, and other SubjectAlternativeNames are present and critical, do
         * not fall back checking CN.
         * </p>
         * <p>
         * If DNS names and IP addresses do not match and the SubjectAlternativeNames are non-critical, fall back to
         * checking CN.
         * </p>
         * @param chain X.509 certificate chain from the server
         */
        private void verifyHostName(final X509Certificate[] chain) throws CertificateException {
            final X500Principal principal = chain[0].getSubjectX500Principal();
            try {
                final List<String> dnsNamePatterns = new ArrayList<>(0);
                final List<String> ipAddresses = new ArrayList<>(0);
                final List<Object> allOthers = new ArrayList<>(0);
                getSanGeneralNames(chain[0], dnsNamePatterns, ipAddresses, allOthers);
                final boolean sanIsCritical = getSanCriticality(chain[0]);

                final InetAddress hostAddress = toIpAddress(hostName);
                if (hostAddress != null) {
                    if (verifyIpAddresses(hostAddress, ipAddresses, principal, sanIsCritical)) {
                        return;
                    }
                } else {
                    if (verifyDnsNamePatterns(hostName, dnsNamePatterns, principal, sanIsCritical)) {
                        return;
                    }
                }
                if (!allOthers.isEmpty() && sanIsCritical) {
                    throw new CertificateException(ERR_CERT_NO_MATCH_ALLOTHERS.get(principal, hostName).toString());
                }

                final DN dn = DN.valueOf(principal.getName(), Schema.getCoreSchema());
                final String certSubjectHostName = getLowestCommonName(dn);
                /* Backwards compatibility: check wildcards in cn */
                if (hostNameMatchesPattern(hostName, certSubjectHostName)) {
                    return;
                }
                throw new CertificateException(ERR_CERT_NO_MATCH_SUBJECT.get(principal, hostName).toString());
            } catch (final CertificateException e) {
                logger.warn(LocalizableMessage.raw("Certificate verification problem for: %s", principal), e);
                throw e;
            }
        }

        /**
         * Collect the general names from a certificate's SubjectAlternativeName extension.
         *
         * General Names can contain: dnsNames, ipAddresses, rfc822Names, x400Addresses, directoryNames, ediPartyNames,
         * uniformResourceIdentifiers, registeredIDs (OID), or otherNames (anything). See
         * {@link X509Certificate#getSubjectAlternativeNames()} for details on how these values are encoded. We separate
         * the dnsNames and ipAddresses (which we can try to match) from everything else (which we do not try to match.)
         *
         * @param subject  certificate
         * @param dnsNames  list where the dnsNames will be added (may be empty)
         * @param ipAddresses  list where the ipAddresses will be added (may be empty)
         * @param allOthers  list where all other general names will be added (may be empty)
         */
        private void getSanGeneralNames(X509Certificate subject,
                                        List<String> dnsNames, List<String> ipAddresses,
                                        List<Object> allOthers) {
            try {
                Collection<List<?>> sans = subject.getSubjectAlternativeNames();
                if (sans == null) {
                    return;
                }
                for (List<?> san : sans) {
                    switch ((Integer) san.get(0)) {
                    case 2:
                        dnsNames.add((String) san.get(1));
                        break;
                    case 7:
                        ipAddresses.add((String) san.get(1));
                        break;
                    default:
                        allOthers.add(san.get(1));
                        break;
                    }
                }
            } catch (CertificateParsingException e) {
                /* do nothing */
            }
        }

        /**
         * Get the ASN.1 criticality of the SubjectAlternativeName extension.
         *
         * @param subject X509Certificate to check
         * @return {@code true} if a subject alt name was found and was marked critical, {@code false} otherwise.
         */
        private boolean getSanCriticality(X509Certificate subject) {
            Set<String> critSet = subject.getCriticalExtensionOIDs();
            return critSet != null && critSet.contains("2.5.29.17");
        }

        /**
         * Convert to an IP address without performing a DNS lookup.
         *
         * @param hostName  either an IP address string, or a host name
         * @return {@code InetAddress} if hostName was an IPv4 or IPv6 address, or {@code null}
         */
        private static InetAddress toIpAddress(String hostName) {
            try {
                if (InetAddressValidator.isValid(hostName)) {
                    return InetAddress.getByName(hostName);
                }
            } catch (UnknownHostException e) {
                /* do nothing */
            }
            return null;
        }

        /**
         * Verify an IP address in the list of IP addresses.
         *
         * @param hostAddress  IP address from the user
         * @param ipAddresses  List of IP addresses from the certificate (may be empty)
         * @param principal  Subject name from the certificate
         * @param failureIsCritical  Should a verification failure throw a {@link CertificateException}
         * @return {@code true} if the address is verified, {@code false} if the address was not verified.
         * @throws CertificateException  if verification fails and {@code failureIsCritical} is {@code true}.
         */
        private boolean verifyIpAddresses(InetAddress hostAddress, List<String> ipAddresses, X500Principal principal,
                                          boolean failureIsCritical) throws CertificateException {
            if (!ipAddresses.isEmpty()) {
                for (String address : ipAddresses) {
                    try {
                        if (InetAddress.getByName(address).equals(hostAddress)) {
                            return true;
                        }
                    } catch (UnknownHostException e) {
                        // do nothing
                    }
                }
                if (failureIsCritical) {
                    // RFC 5280 mentions:
                            /* If the subject field
                             * contains an empty sequence, then the issuing CA MUST include a
                             * subjectAltName extension that is marked as critical.  When including
                             * the subjectAltName extension in a certificate that has a non-empty
                             * subject distinguished name, conforming CAs SHOULD mark the
                             * subjectAltName extension as non-critical.
                             */
                    // Since SAN is critical, the subject is empty, so we cannot perform the next check anyway
                    throw new CertificateException(ERR_CERT_NO_MATCH_IP.get(principal, hostName).toString());
                }
            }
            return false;
        }

        /**
         * Verify a hostname in the list of DNS name patterns.
         *
         * @param hostName  Host name from the user
         * @param dnsNamePatterns  List of DNS name patterns from the certificate (may be empty)
         * @param principal  Subject name from the certificate
         * @param failureIsCritical  Should a verification failure throw a {@link CertificateException}
         * @return {@code true} if the address is verified, {@code false} if the address was not verified.
         * @throws CertificateException  If verification fails and {@code failureIsCritical} is {@code true}
         */
        private boolean verifyDnsNamePatterns(String hostName, List<String> dnsNamePatterns, X500Principal principal,
                              boolean failureIsCritical) throws CertificateException {
            for (String namePattern : dnsNamePatterns) {
                if (hostNameMatchesPattern(hostName, namePattern)) {
                    return true;
                }
            }
            if (failureIsCritical) {
                throw new CertificateException(ERR_CERT_NO_MATCH_DNS.get(principal, hostName).toString());
            }
            return false;
        }

        /**
         * Checks whether a host name matches the provided pattern. It accepts the use of wildcards in the pattern,
         * e.g. {@code *.example.com}.
         *
         * @param hostName
         *            The host name.
         * @param pattern
         *            The host name pattern, which may contain wildcards.
         * @return {@code true} if the host name matched the pattern, otherwise {@code false}.
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

        /**
         * Find the lowest (left-most) cn in the DN, and return its value.
         *
         * @param subject the DN being searched
         * @return the cn value, or {@code null} if no cn was found
         */
        private String getLowestCommonName(DN subject) {
            AttributeType cn = Schema.getDefaultSchema().getAttributeType("cn");
            for (RDN rdn : subject) {
                for (AVA ava : rdn) {
                    if (ava.getAttributeType().equals(cn)) {
                        return ava.getAttributeValue().toString();
                    }
                }
            }
            return null;
        }
    }

    /** An X509TrustManager which rejects certificates which have expired or are not yet valid. */
    private static final class CheckValidityDates implements X509TrustManager {

        private final X509TrustManager trustManager;

        private CheckValidityDates(final X509TrustManager trustManager) {
            this.trustManager = trustManager;
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyExpiration(chain);
            trustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            verifyExpiration(chain);
            trustManager.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        private void verifyExpiration(final X509Certificate[] chain) throws CertificateException {
            final Date currentDate = new Date();
            for (final X509Certificate c : chain) {
                try {
                    c.checkValidity(currentDate);
                } catch (final CertificateExpiredException e) {
                    logger.warn(LocalizableMessage.raw(
                            "Refusing to trust security certificate \'%s\' because it expired on %s",
                            c.getSubjectDN().getName(), c.getNotAfter()));
                    throw e;
                } catch (final CertificateNotYetValidException e) {
                    logger.warn(LocalizableMessage.raw(
                            "Refusing to trust security  certificate \'%s\' because it is not valid until %s",
                            c.getSubjectDN().getName(), c.getNotBefore()));
                    throw e;
                }
            }
        }
    }

    /** An X509TrustManager which does not trust any certificates. */
    private static final class DistrustAll implements X509TrustManager {
        /** Single instance. */
        private static final DistrustAll INSTANCE = new DistrustAll();

        /** Prevent instantiation. */
        private DistrustAll() {
            // Nothing to do.
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            throw new CertificateException();
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            throw new CertificateException();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /** An X509TrustManager which trusts all certificates. */
    private static final class TrustAll implements X509TrustManager {
        /** Single instance. */
        private static final TrustAll INSTANCE = new TrustAll();

        /** Prevent instantiation. */
        private TrustAll() {
            // Nothing to do.
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /**
     * Wraps the provided {@code X509TrustManager} by adding additional validation which rejects certificate chains
     * whose subject alternative names do not match the specified host name or IP address. The check may fall back to
     * checking a hostname in the left-most CN of the subjectDN for backwards compatibility.
     *
     * If the {@code hostName} is an IP address, only the {@code ipAddresses} field of the subject alternative name
     * will be checked. Similarly if {@code hostName} is not an IP address, only the {@code dnsNames} of the subject
     * alternative name will be checked.
     *
     * Host names can be matched using wild cards, for example {@code *.example.com}.
     *
     * If a critical subject alternative name doesn't match, verification will not fall back to checking the subjectDN
     * and will <b>fail</b>. If a critical subject alternative name doesn't match and it contains other kinds of general
     * names that cannot be checked verification will also <b>fail</b>.
     *
     * @param hostName
     *            The IP address or hostname used to connect to the LDAP server which will be matched against the
     *            subject alternative name and possibly the subjectDN as described above.
     * @param trustManager
     *            The trust manager to be wrapped.
     * @return The wrapped trust manager.
     * @throws NullPointerException
     *             If {@code trustManager} or {@code hostName} was {@code null}.
     */
    public static X509TrustManager checkHostName(final String hostName,
            final X509TrustManager trustManager) {
        Reject.ifNull(trustManager, hostName);
        return new CheckHostName(trustManager, hostName);
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

        boolean isFips = isFips();
        final String defaultType = isFips ? "JKS" : KeyStore.getDefaultType();
        final String trustStoreFormat = format != null ? format : defaultType;

        final KeyStore keyStore = KeyStore.getInstance(trustStoreFormat);
        try (FileInputStream fos = new FileInputStream(trustStoreFile)) {
            keyStore.load(fos, password);
        }

        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        for (final TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new NoSuchAlgorithmException();
    }

    public static X509TrustManager checkUsingPkcs12TrustStore() throws GeneralSecurityException, IOException {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        for (final TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new NoSuchAlgorithmException();
    }

    public static boolean isFips() {
		Provider[] providers = Security.getProviders();
		for (int i = 0; i < providers.length; i++) {
			if (providers[i].getName().toLowerCase().contains("fips"))
				return true;
		}

		return false;
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

    /** Prevent instantiation. */
    private TrustManagers() {
        // Nothing to do.
    }
}
