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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Reject;

/**
 * A trust manager which prompts the user for the length of time that they would
 * like to trust a server certificate.
 */
public final class PromptingTrustManager implements X509TrustManager {
    /**
     * Enumeration description server certificate trust option.
     */
    private static enum TrustOption {
        UNTRUSTED(1, INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_NO.get()),
        SESSION(2, INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_SESSION.get()),
        PERMANENT(3, INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_ALWAYS.get()),
        CERTIFICATE_DETAILS(4, INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_DETAILS.get());

        private Integer choice;

        private LocalizableMessage msg;

        /**
         * Private constructor.
         *
         * @param i
         *            the menu return value.
         * @param msg
         *            the message message.
         */
        private TrustOption(final int i, final LocalizableMessage msg) {
            choice = i;
            this.msg = msg;
        }

        /**
         * Returns the choice number.
         *
         * @return the attribute name.
         */
        Integer getChoice() {
            return choice;
        }

        /**
         * Return the menu message.
         *
         * @return the menu message.
         */
        LocalizableMessage getMenuMessage() {
            return msg;
        }
    }

    private static final LocalizedLogger LOG = LocalizedLogger.getLoggerForThisClass();

    private static final String DEFAULT_PATH = System.getProperty("user.home") + File.separator
            + ".opendj" + File.separator + "keystore";

    private static final char[] DEFAULT_PASSWORD = "OpenDJ".toCharArray();

    private final KeyStore inMemoryTrustStore;

    private final KeyStore onDiskTrustStore;

    private final X509TrustManager inMemoryTrustManager;

    private final X509TrustManager onDiskTrustManager;

    private final X509TrustManager nestedTrustManager;

    private final ConsoleApplication app;

    /**
     * Creates a prompting trust manager based on these arguments.
     *
     * @param app
     *            The linked console application.
     * @param acceptedStorePath
     *            The store path.
     * @param sourceTrustManager
     *            The source of the trust manager.
     * @throws KeyStoreException
     *             If no Provider supports a KeyStoreSpi implementation for the specified type.
     * @throws IOException
     *             If there is an I/O or format problem with the keystore data, if a password is required but not given,
     *             or if the given password was incorrect. If the error is due to a wrong password, the cause of the
     *             IOException should be an UnrecoverableKeyException.
     * @throws NoSuchAlgorithmException
     *             If no provider supports a trust manager factory spi implementation for the specified algorithm.
     * @throws CertificateException
     *             If any of the certificates in the key store could not be loaded
     */
    public PromptingTrustManager(final ConsoleApplication app, final String acceptedStorePath,
            final X509TrustManager sourceTrustManager) throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException {
        Reject.ifNull(app, acceptedStorePath);
        this.app = app;
        this.nestedTrustManager = sourceTrustManager;
        inMemoryTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        onDiskTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());

        final File onDiskTrustStorePath = new File(acceptedStorePath);
        inMemoryTrustStore.load(null, null);
        if (!onDiskTrustStorePath.exists()) {
            onDiskTrustStore.load(null, null);
        } else {
            final FileInputStream fos = new FileInputStream(onDiskTrustStorePath);
            try {
                onDiskTrustStore.load(fos, DEFAULT_PASSWORD);
            } finally {
                fos.close();
            }
        }
        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        tmf.init(inMemoryTrustStore);
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
        this.inMemoryTrustManager = x509tm;

        tmf.init(onDiskTrustStore);
        x509tm = null;
        for (final TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                x509tm = (X509TrustManager) tm;
                break;
            }
        }
        if (x509tm == null) {
            throw new NoSuchAlgorithmException();
        }
        this.onDiskTrustManager = x509tm;
    }

    /**
     * Creates a prompting trust manager based on these arguments.
     *
     * @param app
     *            The linked console application.
     * @param sourceTrustManager
     *            The source of the trust manager.
     * @throws KeyStoreException
     *             If no Provider supports a KeyStoreSpi implementation for the specified type.
     * @throws IOException
     *             If there is an I/O or format problem with the keystore data, if a password is required but not given,
     *             or if the given password was incorrect. If the error is due to a wrong password, the cause of the
     *             IOException should be an UnrecoverableKeyException.
     * @throws NoSuchAlgorithmException
     *             If no provider supports a trust manager factory spi implementation for the specified algorithm.
     * @throws CertificateException
     *             If any of the certificates in the key store could not be loaded
     */
    public PromptingTrustManager(final ConsoleApplication app, final X509TrustManager sourceTrustManager)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        this(app, DEFAULT_PATH, sourceTrustManager);
    }

    /** {@inheritDoc} */
    public void checkClientTrusted(final X509Certificate[] x509Certificates, final String s)
            throws CertificateException {
        try {
            inMemoryTrustManager.checkClientTrusted(x509Certificates, s);
        } catch (final Exception ce1) {
            try {
                onDiskTrustManager.checkClientTrusted(x509Certificates, s);
            } catch (final Exception ce2) {
                if (nestedTrustManager != null) {
                    try {
                        nestedTrustManager.checkClientTrusted(x509Certificates, s);
                    } catch (final Exception ce3) {
                        checkManuallyTrusted(x509Certificates, ce3);
                    }
                } else {
                    checkManuallyTrusted(x509Certificates, ce1);
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void checkServerTrusted(final X509Certificate[] x509Certificates, final String s)
            throws CertificateException {
        try {
            inMemoryTrustManager.checkServerTrusted(x509Certificates, s);
        } catch (final Exception ce1) {
            try {
                onDiskTrustManager.checkServerTrusted(x509Certificates, s);
            } catch (final Exception ce2) {
                if (nestedTrustManager != null) {
                    try {
                        nestedTrustManager.checkServerTrusted(x509Certificates, s);
                    } catch (final Exception ce3) {
                        checkManuallyTrusted(x509Certificates, ce3);
                    }
                } else {
                    checkManuallyTrusted(x509Certificates, ce1);
                }
            }
        }
    }

    /** {@inheritDoc} */
    public X509Certificate[] getAcceptedIssuers() {
        if (nestedTrustManager != null) {
            return nestedTrustManager.getAcceptedIssuers();
        }
        return new X509Certificate[0];
    }

    /**
     * This method is called when the user accepted a certificate.
     *
     * @param chain
     *            the certificate chain accepted by the user. certificate.
     */
    private void acceptCertificate(final X509Certificate[] chain, final boolean permanent) {
        if (permanent) {
            LOG.debug(LocalizableMessage.raw("Permanently accepting certificate chain to " + "truststore"));
        } else {
            LOG.debug(LocalizableMessage.raw("Accepting certificate chain for this session"));
        }

        for (final X509Certificate aChain : chain) {
            try {
                final String alias = aChain.getSubjectDN().getName();
                inMemoryTrustStore.setCertificateEntry(alias, aChain);
                if (permanent) {
                    onDiskTrustStore.setCertificateEntry(alias, aChain);
                }
            } catch (final Exception e) {
                LOG.warn(LocalizableMessage.raw("Error setting certificate to store: " + e + "\nCert: " + aChain));
            }
        }

        if (permanent) {
            try {
                final File truststoreFile = new File(DEFAULT_PATH);
                if (!truststoreFile.exists()) {
                    createFile(truststoreFile);
                }
                final FileOutputStream fos = new FileOutputStream(truststoreFile);
                onDiskTrustStore.store(fos, DEFAULT_PASSWORD);
                fos.close();
            } catch (final Exception e) {
                LOG.warn(LocalizableMessage.raw("Error saving store to disk: " + e));
            }
        }
    }

    /**
     * Indicate if the certificate chain can be trusted.
     *
     * @param chain
     *            The certificate chain to validate certificate.
     */
    private void checkManuallyTrusted(final X509Certificate[] chain, final Exception exception)
            throws CertificateException {
        app.println();
        app.println(INFO_LDAP_CONN_PROMPT_SECURITY_SERVER_CERTIFICATE.get());
        app.println();
        for (final X509Certificate element : chain) {
            // Certificate DN
            app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_USER_DN.get(element
                    .getSubjectDN().toString()));

            // certificate validity
            app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_VALIDITY.get(element
                    .getNotBefore().toString(), element.getNotAfter().toString()));

            // certificate Issuer
            app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_ISSUER.get(element.getIssuerDN()
                    .toString()));

            app.println();
            app.println();
        }

        app.println();
        app.println(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION.get());
        app.println();

        final Map<String, TrustOption> menuOptions = new HashMap<>();
        for (final TrustOption t : TrustOption.values()) {
            menuOptions.put(t.getChoice().toString(), t);

            final LocalizableMessageBuilder builder = new LocalizableMessageBuilder();
            builder.append(t.getChoice());
            builder.append(") ");
            builder.append(t.getMenuMessage());
            app.println(builder.toMessage(), 2 /* Indent options */);
        }

        final TrustOption defaultTrustMethod = TrustOption.SESSION;
        final LocalizableMessage promptMsg = INFO_MENU_PROMPT_SINGLE.get();

        while (true) {
            app.println();
            String choice;
            try {
                choice = app.readInput(promptMsg, defaultTrustMethod.getChoice().toString());
            } catch (final ClientException e) {
                // What can we do here?
                throw new CertificateException(exception);
            } finally {
                app.println();
            }

            final TrustOption option = menuOptions.get(choice.trim());
            if (option == null) {
                app.println(ERR_MENU_BAD_CHOICE_SINGLE.get());
                app.println();
                continue;
            }

            switch (option) {
            case UNTRUSTED:
                if (exception instanceof CertificateException) {
                    throw (CertificateException) exception;
                }
                throw new CertificateException(exception);
            case CERTIFICATE_DETAILS:
                for (final X509Certificate aChain : chain) {
                    app.println();
                    app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE.get(aChain.toString()));
                    app.println();
                }
                break;
            default: // SESSION / PERMANENT.
                // Update the trust manager with the new certificate
                acceptCertificate(chain, option == TrustOption.PERMANENT);
                return;
            }
        }
    }

    private boolean createFile(final File f) throws IOException {
        boolean success = false;
        if (f != null) {
            final File parent = f.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            success = f.createNewFile();
        }
        return success;
    }
}
