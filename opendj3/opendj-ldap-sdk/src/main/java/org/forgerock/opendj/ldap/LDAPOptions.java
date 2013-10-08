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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import com.forgerock.opendj.util.Validator;

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
public final class LDAPOptions {
    private SSLContext sslContext;
    private boolean useStartTLS;
    private long timeoutInMillis;
    private DecodeOptions decodeOptions;
    private List<String> enabledCipherSuites = new LinkedList<String>();
    private List<String> enabledProtocols = new LinkedList<String>();
    private ClassLoader providerClassLoader;
    private String transportProvider;

    /**
     * Creates a new set of connection options with default settings. SSL will
     * not be enabled, and a default set of decode options will be used.
     */
    public LDAPOptions() {
        this.decodeOptions = new DecodeOptions();
    }

    /**
     * Creates a new set of connection options having the same initial set of
     * options as the provided set of connection options.
     *
     * @param options
     *            The set of connection options to be copied.
     */
    public LDAPOptions(final LDAPOptions options) {
        this.sslContext = options.sslContext;
        this.timeoutInMillis = options.timeoutInMillis;
        this.useStartTLS = options.useStartTLS;
        this.decodeOptions = new DecodeOptions(options.decodeOptions);
        this.enabledCipherSuites.addAll(options.getEnabledCipherSuites());
        this.enabledProtocols.addAll(options.getEnabledProtocols());
        this.providerClassLoader = options.providerClassLoader;
        this.transportProvider = options.transportProvider;
    }

    /**
     * Returns the decoding options which will be used to control how requests
     * and responses are decoded.
     *
     * @return The decoding options which will be used to control how requests
     *         and responses are decoded (never {@code null}).
     */
    public final DecodeOptions getDecodeOptions() {
        return decodeOptions;
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
    public final SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Returns the operation timeout in the specified unit.
     *
     * @param unit
     *            The time unit of use.
     * @return The operation timeout.
     */
    public final long getTimeout(final TimeUnit unit) {
        return unit.convert(timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Sets the decoding options which will be used to control how requests and
     * responses are decoded.
     *
     * @param decodeOptions
     *            The decoding options which will be used to control how
     *            requests and responses are decoded (never {@code null}).
     * @return A reference to this LDAP connection options.
     * @throws NullPointerException
     *             If {@code decodeOptions} was {@code null}.
     */
    public final LDAPOptions setDecodeOptions(final DecodeOptions decodeOptions) {
        Validator.ensureNotNull(decodeOptions);
        this.decodeOptions = decodeOptions;
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
     * @return A reference to this LDAP connection options.
     */
    public final LDAPOptions setSSLContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Sets the operation timeout. If the response is not received from the
     * Directory Server in the timeout period, the operation will be abandoned
     * and an error result returned. A timeout setting of 0 disables timeout
     * limits.
     *
     * @param timeout
     *            The operation timeout to use.
     * @param unit
     *            the time unit of the time argument.
     * @return A reference to this LDAP connection options.
     */
    public final LDAPOptions setTimeout(final long timeout, final TimeUnit unit) {
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
     * @return A reference to this LDAP connection options.
     */
    public final LDAPOptions setUseStartTLS(final boolean useStartTLS) {
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
    public final boolean useStartTLS() {
        return useStartTLS;
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
     * @return A reference to this LDAP connection options.
     */
    public final LDAPOptions addEnabledProtocol(String... protocols) {
        for (final String protocol : protocols) {
            enabledProtocols.add(Validator.ensureNotNull(protocol));
        }
        return this;
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
     * @return A reference to this LDAP connection options.
     */
    public final LDAPOptions addEnabledCipherSuite(String... suites) {
        for (final String suite : suites) {
            enabledCipherSuites.add(Validator.ensureNotNull(suite));
        }
        return this;
    }

    /**
     * Returns the names of the protocol versions which are currently enabled
     * for secure connections with the Directory Server.
     *
     * @return An array of protocols or empty set if the default protocols are
     *         to be used.
     */
    public final List<String> getEnabledProtocols() {
        return enabledProtocols;
    }

    /**
     * Returns the names of the protocol versions which are currently enabled
     * for secure connections with the Directory Server.
     *
     * @return An array of protocols or empty set if the default protocols are
     *         to be used.
     */
    public final List<String> getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    /**
     * Gets the class loader which will be used to load the
     * {@code TransportProvider}.
     * <p>
     * By default this method will return {@code null} indicating that the
     * default class loader will be used.
     * <p>
     * The transport provider is loaded using {@code java.util.ServiceLoader},
     * the JDK service-provider loading facility. The provider must be
     * accessible from the same class loader that was initially queried to
     * locate the configuration file; note that this is not necessarily the
     * class loader from which the file was actually loaded. This method allows
     * to provide a class loader to be used for loading the provider.
     *
     * @return The class loader which will be used when loading the transport
     *         provider, or {@code null} if the default class loader should be
     *         used.
     */
    public final ClassLoader getProviderClassLoader() {
        return providerClassLoader;
    }

    /**
     * Sets the class loader which will be used to load the
     * {@code TransportProvider}.
     * <p>
     * The default class loader will be used if no class loader is set using
     * this method.
     * <p>
     * The transport provider is loaded using {@code java.util.ServiceLoader},
     * the JDK service-provider loading facility. The provider must be
     * accessible from the same class loader that was initially queried to
     * locate the configuration file; note that this is not necessarily the
     * class loader from which the file was actually loaded. This method allows
     * to provide a class loader to be used for loading the provider.
     *
     * @param classLoader
     *            The class loader which will be used when loading the transport
     *            provider, or {@code null} if the default class loader should
     *            be used.
     * @return A reference to this LDAP connection options.
     */
    public final LDAPOptions setProviderClassLoader(ClassLoader classLoader) {
        this.providerClassLoader = classLoader;
        return this;
    }

    /**
     * Returns the name of the provider used for transport.
     * <p>
     * Transport providers implement {@code TransportProvider}
     * interface.
     * <p>
     * The name should correspond to the name of an existing provider, as
     * returned by {@code TransportProvider#getName()} method.
     *
     * @return The name of transport provider. The name is {@code null} if no
     *         specific provider has been selected. In that case, the first
     *         provider found will be used.
     */
    public String getTransportProvider() {
        return transportProvider;
    }

    /**
     * Sets the name of the provider to use for transport.
     * <p>
     * Transport providers implement {@code TransportProvider}
     * interface.
     * <p>
     * The name should correspond to the name of an existing provider, as
     * returned by {@code TransportProvider#getName()} method.
     *
     * @param providerName
     *            The name of transport provider, or {@code null} if no specific
     *            provider is preferred. In that case, the first provider found
     *            will be used.
     * @return A reference to this LDAP connection options.
     */
    public LDAPOptions setTransportProvider(String providerName) {
        this.transportProvider = providerName;
        return this;
    }

}
