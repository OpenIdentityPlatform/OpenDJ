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
 *      Copyright 2014 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.forgerock.util.Reject;

/**
 * Common options for LDAP clients and listeners.
 */
abstract class CommonLDAPOptions<T extends CommonLDAPOptions<T>> {
    /** Default values for options taken from Java properties. */
    private static final boolean DEFAULT_TCP_NO_DELAY;
    private static final boolean DEFAULT_REUSE_ADDRESS;
    private static final boolean DEFAULT_KEEPALIVE;
    private static final int DEFAULT_LINGER;
    static {
        DEFAULT_LINGER = getIntProperty("org.forgerock.opendj.io.linger", -1);
        DEFAULT_TCP_NO_DELAY = getBooleanProperty("org.forgerock.opendj.io.tcpNoDelay", true);
        DEFAULT_REUSE_ADDRESS = getBooleanProperty("org.forgerock.opendj.io.reuseAddress", true);
        DEFAULT_KEEPALIVE = getBooleanProperty("org.forgerock.opendj.io.keepAlive", true);
    }

    static boolean getBooleanProperty(final String name, final boolean defaultValue) {
        final String value = System.getProperty(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    static int getIntProperty(final String name, final int defaultValue) {
        final String value = System.getProperty(name);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    private DecodeOptions decodeOptions;
    private ClassLoader providerClassLoader;
    private String transportProvider;
    private boolean tcpNoDelay = DEFAULT_TCP_NO_DELAY;
    private boolean keepAlive = DEFAULT_KEEPALIVE;
    private boolean reuseAddress = DEFAULT_REUSE_ADDRESS;
    private int linger = DEFAULT_LINGER;

    CommonLDAPOptions() {
        this.decodeOptions = new DecodeOptions();
    }

    CommonLDAPOptions(final CommonLDAPOptions<?> options) {
        this.decodeOptions = new DecodeOptions(options.decodeOptions);
        this.providerClassLoader = options.providerClassLoader;
        this.transportProvider = options.transportProvider;
        this.linger = options.linger;
        this.keepAlive = options.keepAlive;
        this.reuseAddress = options.reuseAddress;
        this.tcpNoDelay = options.tcpNoDelay;
    }

    /**
     * Returns the decoding options which will be used to control how requests
     * and responses are decoded.
     *
     * @return The decoding options which will be used to control how requests
     *         and responses are decoded (never {@code null}).
     */
    public DecodeOptions getDecodeOptions() {
        return decodeOptions;
    }

    /**
     * Returns the value of the {@link java.net.SocketOptions#SO_LINGER
     * SO_LINGER} socket option for new connections.
     * <p>
     * The default setting is {@code -1} (disabled) and may be configured using
     * the {@code org.forgerock.opendj.io.linger} property.
     *
     * @return The value of the {@link java.net.SocketOptions#SO_LINGER
     *         SO_LINGER} socket option for new connections, or -1 if linger
     *         should be disabled.
     */
    public int getLinger() {
        return linger;
    }

    /**
     * Returns the class loader which will be used to load the
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
    public ClassLoader getProviderClassLoader() {
        return providerClassLoader;
    }

    /**
     * Returns the name of the provider used for transport.
     * <p>
     * Transport providers implement {@code TransportProvider} interface.
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
     * Returns the value of the {@link java.net.SocketOptions#SO_KEEPALIVE
     * SO_KEEPALIVE} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.keepAlive} property.
     *
     * @return The value of the {@link java.net.SocketOptions#SO_KEEPALIVE
     *         SO_KEEPALIVE} socket option for new connections.
     */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Returns the value of the {@link java.net.SocketOptions#SO_REUSEADDR
     * SO_REUSEADDR} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.reuseAddress} property.
     *
     * @return The value of the {@link java.net.SocketOptions#SO_REUSEADDR
     *         SO_REUSEADDR} socket option for new connections.
     */
    public boolean isReuseAddress() {
        return reuseAddress;
    }

    /**
     * Returns the value of the {@link java.net.SocketOptions#TCP_NODELAY
     * TCP_NODELAY} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.tcpNoDelay} property.
     *
     * @return The value of the {@link java.net.SocketOptions#TCP_NODELAY
     *         TCP_NODELAY} socket option for new connections.
     */
    public boolean isTCPNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Sets the decoding options which will be used to control how requests and
     * responses are decoded.
     *
     * @param decodeOptions
     *            The decoding options which will be used to control how
     *            requests and responses are decoded (never {@code null}).
     * @return A reference to this set of options.
     * @throws NullPointerException
     *             If {@code decodeOptions} was {@code null}.
     */
    public T setDecodeOptions(final DecodeOptions decodeOptions) {
        Reject.ifNull(decodeOptions);
        this.decodeOptions = decodeOptions;
        return getThis();
    }

    /**
     * Specifies the value of the {@link java.net.SocketOptions#SO_KEEPALIVE
     * SO_KEEPALIVE} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.keepAlive} property.
     *
     * @param keepAlive
     *            The value of the {@link java.net.SocketOptions#SO_KEEPALIVE
     *            SO_KEEPALIVE} socket option for new connections.
     * @return A reference to this set of options.
     */
    public T setKeepAlive(final boolean keepAlive) {
        this.keepAlive = keepAlive;
        return getThis();
    }

    /**
     * Specifies the value of the {@link java.net.SocketOptions#SO_LINGER
     * SO_LINGER} socket option for new connections.
     * <p>
     * The default setting is {@code -1} (disabled) and may be configured using
     * the {@code org.forgerock.opendj.io.linger} property.
     *
     * @param linger
     *            The value of the {@link java.net.SocketOptions#SO_LINGER
     *            SO_LINGER} socket option for new connections, or -1 if linger
     *            should be disabled.
     * @return A reference to this set of options.
     */
    public T setLinger(final int linger) {
        this.linger = linger;
        return getThis();
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
     * @return A reference to this set of options.
     */
    public T setProviderClassLoader(final ClassLoader classLoader) {
        this.providerClassLoader = classLoader;
        return getThis();
    }

    /**
     * Specifies the value of the {@link java.net.SocketOptions#SO_REUSEADDR
     * SO_REUSEADDR} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.reuseAddress} property.
     *
     * @param reuseAddress
     *            The value of the {@link java.net.SocketOptions#SO_REUSEADDR
     *            SO_REUSEADDR} socket option for new connections.
     * @return A reference to this set of options.
     */
    public T setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return getThis();
    }

    /**
     * Specifies the value of the {@link java.net.SocketOptions#TCP_NODELAY
     * TCP_NODELAY} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.tcpNoDelay} property.
     *
     * @param tcpNoDelay
     *            The value of the {@link java.net.SocketOptions#TCP_NODELAY
     *            TCP_NODELAY} socket option for new connections.
     * @return A reference to this set of options.
     */
    public T setTCPNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return getThis();
    }

    /**
     * Sets the name of the provider to use for transport.
     * <p>
     * Transport providers implement {@code TransportProvider} interface.
     * <p>
     * The name should correspond to the name of an existing provider, as
     * returned by {@code TransportProvider#getName()} method.
     *
     * @param providerName
     *            The name of transport provider, or {@code null} if no specific
     *            provider is preferred. In that case, the first provider found
     *            will be used.
     * @return A reference to this set of options.
     */
    public T setTransportProvider(final String providerName) {
        this.transportProvider = providerName;
        return getThis();
    }

    abstract T getThis();
}
