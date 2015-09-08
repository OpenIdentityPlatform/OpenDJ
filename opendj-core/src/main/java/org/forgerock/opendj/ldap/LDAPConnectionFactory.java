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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.getProvider;

import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

import javax.net.ssl.SSLContext;
import java.util.Collections;
import java.util.List;

/**
 * A factory class which can be used to obtain connections to an LDAP Directory
 * Server.
 */
public final class LDAPConnectionFactory extends CommonLDAPOptions implements ConnectionFactory {

    /**
     * Specifies the SSL context which will be used when initiating connections with the Directory Server.
     * <p>
     * By default no SSL context will be used, indicating that connections will not be secured.
     * If an SSL context is set then connections will be secured using either SSL or StartTLS
     * depending on {@link #USE_STARTTLS}.
     */
    public static final Option<SSLContext> SSL_CONTEXT = Option.of(SSLContext.class, null);
    /**
     * Specifies whether SSL or StartTLS should be used for securing connections when an SSL context is specified.
     * <p>
     * By default SSL will be used in preference to StartTLS.
     */
    public static final Option<Boolean> USE_STARTTLS = Option.withDefault(false);
    /**
     * Specifies the operation timeout in milliseconds. If a response is not
     * received from the Directory Server within the timeout period, then the
     * operation will be abandoned and a {@link TimeoutResultException} error
     * result returned. A timeout setting of 0 disables operation timeout limits.
     * <p>
     * The default operation timeout is 0 (no timeout) and may be configured
     * using the {@code org.forgerock.opendj.io.timeout} property.
     */
    public static final Option<Long> TIMEOUT_IN_MILLISECONDS = Option.of(Long.class,
        (long) getIntProperty("org.forgerock.opendj.io.timeout", 0));
    /**
     * Specifies the connect timeout spcified in milliseconds. If a connection is not established
     * within the timeout period, then a {@link TimeoutResultException} error result will be returned.
     * <p>
     * The default operation timeout is 10 seconds and may be configured using
     * the {@code org.forgerock.opendj.io.connectTimeout} property.
     * A timeout setting of 0 causes the OS connect timeout to be used.
     */
    public static final Option<Long> CONNECT_TIMEOUT_IN_MILLISECONDS = Option.of(Long.class,
        (long) getIntProperty("org.forgerock.opendj.io.connectTimeout", 10000));
    /**
     * Specifies the cipher suites enabled for secure connections with the Directory Server.
     * <p>
     * The suites must be supported by the SSLContext specified by option {@link SSL_CONTEXT}.
     * Only the suites listed in the parameter are enabled for use.
     */
    public static final Option<List<String>> ENABLED_CIPHER_SUITES = Option.withDefault(
        Collections.<String>emptyList());
    /**
     * Specifies the protocol versions enabled for secure connections with the
     * Directory Server.
     * <p>
     * The protocols must be supported by the SSLContext specified by option {@link SSL_CONTEXT}.
     * Only the protocols listed in the parameter are enabled for use.
     */
    public static final Option<List<String>> ENABLED_PROTOCOLS =
        Option.withDefault(Collections.<String>emptyList());

    /**
     * We implement the factory using the pimpl idiom in order to avoid making
     * too many implementation classes public.
     */
    private final LDAPConnectionFactoryImpl impl;

    /**
     * Transport provider that provides the implementation of this factory.
     */
    private final TransportProvider provider;

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided host and port
     * number.
     *
     * @param host The host name.
     * @param port The port number.
     * @throws NullPointerException      If {@code host} was {@code null}.
     * @throws ProviderNotFoundException if no provider is available or if the
     *                                   provider requested using options is not found.
     */
    public LDAPConnectionFactory(final String host, final int port) {
        this(host, port, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP connection factory which can be used to create LDAP
     * connections to the Directory Server at the provided host and port
     * number.
     *
     * @param host    The host name.
     * @param port    The port number.
     * @param options The LDAP options to use when creating connections.
     * @throws NullPointerException      If {@code host} or {@code options} was {@code null}.
     * @throws ProviderNotFoundException if no provider is available or if the
     *                                   provider requested using options is not found.
     */
    public LDAPConnectionFactory(final String host, final int port, final Options options) {
        Reject.ifNull(host, options);
        this.provider = getProvider(TransportProvider.class, options.get(TRANSPORT_PROVIDER),
                options.get(PROVIDER_CLASS_LOADER));
        this.impl = provider.getLDAPConnectionFactory(host, port, options);
    }

    @Override
    public void close() {
        impl.close();
    }

    @Override
    public Promise<Connection, LdapException> getConnectionAsync() {
        return impl.getConnectionAsync();
    }

    @Override
    public Connection getConnection() throws LdapException {
        return impl.getConnection();
    }

    /**
     * Returns the host name of the Directory Server. The returned host name is
     * the same host name that was provided during construction and may be an IP
     * address. More specifically, this method will not perform a reverse DNS
     * lookup.
     *
     * @return The host name of the Directory Server.
     */
    public String getHostName() {
        return impl.getHostName();
    }

    /**
     * Returns the port of the Directory Server.
     *
     * @return The port of the Directory Server.
     */
    public int getPort() {
        return impl.getPort();
    }

    /**
     * Returns the name of the transport provider, which provides the implementation
     * of this factory.
     *
     * @return The name of actual transport provider.
     */
    public String getProviderName() {
        return provider.getName();
    }

    @Override
    public String toString() {
        return impl.toString();
    }
}
