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

import static com.forgerock.opendj.util.Validator.ensureNotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

/**
 * Common options for LDAP client connections.
 * <p>
 * For example you set LDAP options when you want to use StartTLS.
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
public final class LDAPOptions extends CommonLDAPOptions<LDAPOptions> {
    // Default values for options taken from Java properties.
    private static final long DEFAULT_TIMEOUT;
    private static final long DEFAULT_CONNECT_TIMEOUT;
    static {
        DEFAULT_TIMEOUT = getIntProperty("org.forgerock.opendj.io.timeout", 0);
        DEFAULT_CONNECT_TIMEOUT = getIntProperty("org.forgerock.opendj.io.connectTimeout", 5000);
    }

    private SSLContext sslContext;
    private boolean useStartTLS;
    private long timeoutInMillis = DEFAULT_TIMEOUT;
    private long connectTimeoutInMillis = DEFAULT_CONNECT_TIMEOUT;
    private final List<String> enabledCipherSuites = new LinkedList<String>();
    private final List<String> enabledProtocols = new LinkedList<String>();

    /**
     * Creates a new set of connection options with default settings. SSL will
     * not be enabled, and a default set of decode options will be used.
     */
    public LDAPOptions() {
        super();
    }

    /**
     * Creates a new set of connection options having the same initial set of
     * options as the provided set of connection options.
     *
     * @param options
     *            The set of connection options to be copied.
     */
    public LDAPOptions(final LDAPOptions options) {
        super(options);
        this.sslContext = options.sslContext;
        this.timeoutInMillis = options.timeoutInMillis;
        this.useStartTLS = options.useStartTLS;
        this.enabledCipherSuites.addAll(options.getEnabledCipherSuites());
        this.enabledProtocols.addAll(options.getEnabledProtocols());
        this.connectTimeoutInMillis = options.connectTimeoutInMillis;
    }

    /**
     * Adds the cipher suites enabled for secure connections with the Directory
     * Server.
     * <p>
     * The suites must be supported by the SSLContext specified in
     * {@link #setSSLContext(SSLContext)}. Following a successful call to this
     * method, only the suites listed in the protocols parameter are enabled for
     * use.
     *
     * @param suites
     *            Names of all the suites to enable.
     * @return A reference to this set of options.
     */
    public LDAPOptions addEnabledCipherSuite(final String... suites) {
        for (final String suite : suites) {
            enabledCipherSuites.add(ensureNotNull(suite));
        }
        return this;
    }

    /**
     * Adds the protocol versions enabled for secure connections with the
     * Directory Server.
     * <p>
     * The protocols must be supported by the SSLContext specified in
     * {@link #setSSLContext(SSLContext)}. Following a successful call to this
     * method, only the protocols listed in the protocols parameter are enabled
     * for use.
     *
     * @param protocols
     *            Names of all the protocols to enable.
     * @return A reference to this set of options.
     */
    public LDAPOptions addEnabledProtocol(final String... protocols) {
        for (final String protocol : protocols) {
            enabledProtocols.add(ensureNotNull(protocol));
        }
        return this;
    }

    /**
     * Returns the connect timeout in the specified unit. If a connection is not
     * established within the timeout period, then a
     * {@link TimeoutResultException} error result will be returned. A timeout
     * setting of 0 causes the OS connect timeout to be used.
     * <p>
     * The default operation timeout is 10 seconds and may be configured using
     * the {@code org.forgerock.opendj.io.connectTimeout} property.
     *
     * @param unit
     *            The time unit.
     * @return The connect timeout, which may be 0 if there is no connect
     *         timeout.
     */
    public long getConnectTimeout(final TimeUnit unit) {
        return unit.convert(connectTimeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the names of the protocol versions which are currently enabled
     * for secure connections with the Directory Server.
     *
     * @return An array of protocols or empty set if the default protocols are
     *         to be used.
     */
    public List<String> getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    /**
     * Returns the names of the protocol versions which are currently enabled
     * for secure connections with the Directory Server.
     *
     * @return An array of protocols or empty set if the default protocols are
     *         to be used.
     */
    public List<String> getEnabledProtocols() {
        return enabledProtocols;
    }

    /**
     * Returns the SSL context which will be used when initiating connections
     * with the Directory Server.
     * <p>
     * By default no SSL context will be used, indicating that connections will
     * not be secured. If a non-{@code null} SSL context is returned then
     * connections will be secured using either SSL or StartTLS depending on
     * {@link #useStartTLS()}.
     *
     * @return The SSL context which will be used when initiating secure
     *         connections with the Directory Server, which may be {@code null}
     *         indicating that connections will not be secured.
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Returns the operation timeout in the specified unit. If a response is not
     * received from the Directory Server within the timeout period, then the
     * operation will be abandoned and a {@link TimeoutResultException} error
     * result returned. A timeout setting of 0 disables operation timeout
     * limits.
     * <p>
     * The default operation timeout is 0 (no timeout) and may be configured
     * using the {@code org.forgerock.opendj.io.timeout} property.
     *
     * @param unit
     *            The time unit.
     * @return The operation timeout, which may be 0 if there is no operation
     *         timeout.
     */
    public long getTimeout(final TimeUnit unit) {
        return unit.convert(timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Sets the connect timeout. If a connection is not established within the
     * timeout period, then a {@link TimeoutResultException} error result will
     * be returned. A timeout setting of 0 causes the OS connect timeout to be
     * used.
     * <p>
     * The default operation timeout is 10 seconds and may be configured using
     * the {@code org.forgerock.opendj.io.connectTimeout} property.
     *
     * @param timeout
     *            The connect timeout, which may be 0 if there is no connect
     *            timeout.
     * @param unit
     *            The time unit.
     * @return A reference to this set of options.
     */
    public LDAPOptions setConnectTimeout(final long timeout, final TimeUnit unit) {
        this.connectTimeoutInMillis = unit.toMillis(timeout);
        return this;
    }

    /**
     * Sets the SSL context which will be used when initiating connections with
     * the Directory Server.
     * <p>
     * By default no SSL context will be used, indicating that connections will
     * not be secured. If a non-{@code null} SSL context is returned then
     * connections will be secured using either SSL or StartTLS depending on
     * {@link #useStartTLS()}.
     *
     * @param sslContext
     *            The SSL context which will be used when initiating secure
     *            connections with the Directory Server, which may be
     *            {@code null} indicating that connections will not be secured.
     * @return A reference to this set of options.
     */
    public LDAPOptions setSSLContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Sets the operation timeout. If a response is not received from the
     * Directory Server within the timeout period, then the operation will be
     * abandoned and a {@link TimeoutResultException} error result returned. A
     * timeout setting of 0 disables operation timeout limits.
     * <p>
     * The default operation timeout is 0 (no timeout) and may be configured
     * using the {@code org.forgerock.opendj.io.timeout} property.
     *
     * @param timeout
     *            The operation timeout, which may be 0 if there is no operation
     *            timeout.
     * @param unit
     *            The time unit.
     * @return A reference to this set of options.
     */
    public LDAPOptions setTimeout(final long timeout, final TimeUnit unit) {
        this.timeoutInMillis = unit.toMillis(timeout);
        return this;
    }

    /**
     * Specifies whether or not SSL or StartTLS should be used for securing
     * connections when an SSL context is specified.
     * <p>
     * By default SSL will be used in preference to StartTLS.
     *
     * @param useStartTLS
     *            {@code true} if StartTLS should be used for securing
     *            connections when an SSL context is specified, otherwise
     *            {@code false} indicating that SSL should be used.
     * @return A reference to this set of options.
     */
    public LDAPOptions setUseStartTLS(final boolean useStartTLS) {
        this.useStartTLS = useStartTLS;
        return this;
    }

    /**
     * Indicates whether or not SSL or StartTLS should be used for securing
     * connections when an SSL context is specified.
     * <p>
     * By default SSL will be used in preference to StartTLS.
     *
     * @return {@code true} if StartTLS should be used for securing connections
     *         when an SSL context is specified, otherwise {@code false}
     *         indicating that SSL should be used.
     */
    public boolean useStartTLS() {
        return useStartTLS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LDAPOptions setDecodeOptions(DecodeOptions decodeOptions) {
        // This method is required for binary compatibility.
        return super.setDecodeOptions(decodeOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LDAPOptions setTCPNIOTransport(TCPNIOTransport transport) {
        // This method is required for binary compatibility.
        return super.setTCPNIOTransport(transport);
    }

    @Override
    LDAPOptions getThis() {
        return this;
    }
}
