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
 *      Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.getProvider;

import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Option;
import org.forgerock.util.Options;

/**
 * Common options for LDAP clients and listeners.
 */
abstract class CommonLDAPOptions {
    /**
     * Specifies the class loader which will be used to load the
     * {@link org.forgerock.opendj.ldap.spi.TransportProvider TransportProvider}.
     * <p>
     * By default the default class loader will be used.
     * <p>
     * The transport provider is loaded using {@code java.util.ServiceLoader},
     * the JDK service-provider loading facility. The provider must be
     * accessible from the same class loader that was initially queried to
     * locate the configuration file; note that this is not necessarily the
     * class loader from which the file was actually loaded. This method allows
     * to provide a class loader to be used for loading the provider.
     *
     */
    public static final Option<ClassLoader> TRANSPORT_PROVIDER_CLASS_LOADER = Option.of(ClassLoader.class, null);

    /**
     * Specifies the name of the provider to use for transport.
     * <p>
     * Transport providers implement {@link org.forgerock.opendj.ldap.spi.TransportProvider TransportProvider}
     * interface.
     * <p>
     * The name should correspond to the name of an existing provider, as
     * returned by {@code TransportProvider#getName()} method.
     */
    public static final Option<String> TRANSPORT_PROVIDER = Option.of(String.class, null);

    /**
     * Specifies the transport provider to use. This option is internal and only intended for testing.
     */
    static final Option<TransportProvider> TRANSPORT_PROVIDER_INSTANCE = Option.of(TransportProvider.class, null);

    /**
     * Specifies the value of the {@link java.net.SocketOptions#TCP_NODELAY
     * TCP_NODELAY} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.tcpNoDelay} property.
     */
    public static final Option<Boolean> TCP_NO_DELAY = Option.withDefault(
        getBooleanProperty("org.forgerock.opendj.io.tcpNoDelay", true));

    /**
     * Specifies the value of the {@link java.net.SocketOptions#SO_REUSEADDR
     * SO_REUSEADDR} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.reuseAddress} property.
     *
     */
    public static final Option<Boolean> SO_REUSE_ADDRESS = Option.withDefault(
        getBooleanProperty("org.forgerock.opendj.io.reuseAddress", true));

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
    public static final Option<Integer> SO_LINGER_IN_SECONDS = Option.withDefault(
        getIntProperty("org.forgerock.opendj.io.linger", -1));

    /**
     * Specifies the value of the {@link java.net.SocketOptions#SO_KEEPALIVE
     * SO_KEEPALIVE} socket option for new connections.
     * <p>
     * The default setting is {@code true} and may be configured using the
     * {@code org.forgerock.opendj.io.keepAlive} property.
     *
     */
    public static final Option<Boolean> SO_KEEPALIVE = Option.withDefault(
        getBooleanProperty("org.forgerock.opendj.io.keepAlive", true));

    /** Sets the decoding options which will be used to control how requests and responses are decoded. */
    public static final Option<DecodeOptions> LDAP_DECODE_OPTIONS = Option.withDefault(new DecodeOptions());

    static TransportProvider getTransportProvider(final Options options) {
        final TransportProvider transportProvider = options.get(TRANSPORT_PROVIDER_INSTANCE);
        if (transportProvider != null) {
            return transportProvider;
        }
        return getProvider(TransportProvider.class,
                           options.get(TRANSPORT_PROVIDER),
                           options.get(TRANSPORT_PROVIDER_CLASS_LOADER));
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
}
