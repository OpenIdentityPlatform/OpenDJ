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
 */

package org.forgerock.opendj.ldap;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * An SSL context builder provides an interface for incrementally constructing
 * {@link SSLContext} instances for use when securing connections with SSL or
 * the StartTLS extended operation. The {@link #getSSLContext()} should be
 * called in order to obtain the {@code SSLContext}.
 * <p>
 * For example, use the SSL context builder when setting up LDAP options needed
 * to use StartTLS. {@link org.forgerock.opendj.ldap.TrustManagers
 * TrustManagers} has methods you can use to set the trust manager for the SSL
 * context builder.
 *
 * <pre>
 * LDAPOptions options = new LDAPOptions();
 * SSLContext sslContext =
 *         new SSLContextBuilder().setTrustManager(...).getSSLContext();
 * options.setSSLContext(sslContext);
 * options.setUseStartTLS(true);
 *
 * String host = ...;
 * int port = ...;
 * LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port, options);
 * Connection connection = factory.getConnection();
 * // Connection uses StartTLS...
 * </pre>
 */
public final class SSLContextBuilder {

    /**
     * SSL protocol: supports some version of SSL; may support other versions.
     */
    public static final String PROTOCOL_SSL = "SSL";

    /**
     * SSL protocol: supports SSL version 2 or higher; may support other
     * versions.
     */
    public static final String PROTOCOL_SSL2 = "SSLv2";

    /**
     * SSL protocol: supports SSL version 3; may support other versions.
     */
    public static final String PROTOCOL_SSL3 = "SSLv3";

    /**
     * SSL protocol: supports some version of TLS; may support other versions.
     */
    public static final String PROTOCOL_TLS = "TLS";

    /**
     * SSL protocol: supports RFC 2246: TLS version 1.0 ; may support other
     * versions.
     */
    public static final String PROTOCOL_TLS1 = "TLSv1";

    /**
     * SSL protocol: supports RFC 4346: TLS version 1.1 ; may support other
     * versions.
     */
    public static final String PROTOCOL_TLS1_1 = "TLSv1.1";

    private TrustManager trustManager;
    private KeyManager keyManager;
    private String protocol = PROTOCOL_TLS1;
    private SecureRandom random;

    /** These are mutually exclusive. */
    private Provider provider;
    private String providerName;

    /**
     * Creates a new SSL context builder using default parameters.
     */
    public SSLContextBuilder() {
        // Do nothing.
    }

    /**
     * Creates a {@code SSLContext} using the parameters of this SSL context
     * builder.
     *
     * @return A {@code SSLContext} using the parameters of this SSL context
     *         builder.
     * @throws GeneralSecurityException
     *             If the SSL context could not be created, perhaps due to
     *             missing algorithms.
     */
    public SSLContext getSSLContext() throws GeneralSecurityException {
        TrustManager[] tm = null;
        if (trustManager != null) {
            tm = new TrustManager[] { trustManager };
        }

        KeyManager[] km = null;
        if (keyManager != null) {
            km = new KeyManager[] { keyManager };
        }

        SSLContext sslContext;
        if (provider != null) {
            sslContext = SSLContext.getInstance(protocol, provider);
        } else if (providerName != null) {
            sslContext = SSLContext.getInstance(protocol, providerName);
        } else {
            sslContext = SSLContext.getInstance(protocol);
        }
        sslContext.init(km, tm, random);

        return sslContext;
    }

    /**
     * Sets the key manager which the SSL context should use. By default, no key
     * manager is specified indicating that no certificates will be used.
     *
     * @param keyManager
     *            The key manager which the SSL context should use, which may be
     *            {@code null} indicating that no certificates will be used.
     * @return This SSL context builder.
     */
    public SSLContextBuilder setKeyManager(final KeyManager keyManager) {
        this.keyManager = keyManager;
        return this;
    }

    /**
     * Sets the protocol which the SSL context should use. By default, TLSv1
     * will be used.
     *
     * @param protocol
     *            The protocol which the SSL context should use, which may be
     *            {@code null} indicating that TLSv1 will be used.
     * @return This SSL context builder.
     */
    public SSLContextBuilder setProtocol(final String protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * Sets the provider which the SSL context should use. By default, the
     * default provider associated with this JVM will be used.
     *
     * @param provider
     *            The provider which the SSL context should use, which may be
     *            {@code null} indicating that the default provider associated
     *            with this JVM will be used.
     * @return This SSL context builder.
     */
    public SSLContextBuilder setProvider(final Provider provider) {
        this.provider = provider;
        this.providerName = null;
        return this;
    }

    /**
     * Sets the provider which the SSL context should use. By default, the
     * default provider associated with this JVM will be used.
     *
     * @param providerName
     *            The name of the provider which the SSL context should use,
     *            which may be {@code null} indicating that the default provider
     *            associated with this JVM will be used.
     * @return This SSL context builder.
     */
    public SSLContextBuilder setProvider(final String providerName) {
        this.provider = null;
        this.providerName = providerName;
        return this;
    }

    /**
     * Sets the secure random number generator which the SSL context should use.
     * By default, the default secure random number generator associated with
     * this JVM will be used.
     *
     * @param random
     *            The secure random number generator which the SSL context
     *            should use, which may be {@code null} indicating that the
     *            default secure random number generator associated with this
     *            JVM will be used.
     * @return This SSL context builder.
     */
    public SSLContextBuilder setSecureRandom(final SecureRandom random) {
        this.random = random;
        return this;
    }

    /**
     * Sets the trust manager which the SSL context should use. By default, no
     * trust manager is specified indicating that only certificates signed by
     * the authorities associated with this JVM will be accepted.
     *
     * @param trustManager
     *            The trust manager which the SSL context should use, which may
     *            be {@code null} indicating that only certificates signed by
     *            the authorities associated with this JVM will be accepted.
     * @return This SSL context builder.
     */
    public SSLContextBuilder setTrustManager(final TrustManager trustManager) {
        this.trustManager = trustManager;
        return this;
    }
}
