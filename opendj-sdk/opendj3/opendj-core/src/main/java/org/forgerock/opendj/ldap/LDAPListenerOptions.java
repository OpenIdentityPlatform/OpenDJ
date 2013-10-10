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

import com.forgerock.opendj.util.Validator;

/**
 * Common options for LDAP listeners.
 */
public final class LDAPListenerOptions {

    private int backlog;
    private DecodeOptions decodeOptions;
    private int maxRequestSize;
    private ClassLoader providerClassLoader;
    private String transportProvider;

    /**
     * Creates a new set of listener options with default settings. SSL will not
     * be enabled, and a default set of decode options will be used.
     */
    public LDAPListenerOptions() {
        this.decodeOptions = new DecodeOptions();
    }

    /**
     * Creates a new set of listener options having the same initial set of
     * options as the provided set of listener options.
     *
     * @param options
     *            The set of listener options to be copied.
     */
    public LDAPListenerOptions(final LDAPListenerOptions options) {
        this.backlog = options.backlog;
        this.maxRequestSize = options.maxRequestSize;
        this.decodeOptions = new DecodeOptions(options.decodeOptions);
        this.providerClassLoader = options.providerClassLoader;
        this.transportProvider = options.transportProvider;
    }

    /**
     * Returns the maximum queue length for incoming connections requests. If a
     * connection request arrives when the queue is full, the connection is
     * refused. If the backlog is less than {@code 1} then a default value of
     * {@code 50} will be used.
     *
     * @return The maximum queue length for incoming connections requests.
     */
    public int getBacklog() {
        return backlog;
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
     * Returns the maximum request size in bytes for incoming LDAP requests. If
     * an incoming request exceeds the limit then the connection will be aborted
     * by the listener. If the limit is less than {@code 1} then a default value
     * of {@code 5MB} will be used.
     *
     * @return The maximum request size in bytes for incoming LDAP requests.
     */
    public int getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * Sets the maximum queue length for incoming connections requests. If a
     * connection request arrives when the queue is full, the connection is
     * refused. If the backlog is less than {@code 1} then a default value of
     * {@code 50} will be used.
     *
     * @param backlog
     *            The maximum queue length for incoming connections requests.
     * @return A reference to this LDAP listener options.
     */
    public LDAPListenerOptions setBacklog(final int backlog) {
        this.backlog = backlog;
        return this;
    }

    /**
     * Sets the decoding options which will be used to control how requests and
     * responses are decoded.
     *
     * @param decodeOptions
     *            The decoding options which will be used to control how
     *            requests and responses are decoded (never {@code null}).
     * @return A reference to this LDAP listener options.
     * @throws NullPointerException
     *             If {@code decodeOptions} was {@code null}.
     */
    public LDAPListenerOptions setDecodeOptions(final DecodeOptions decodeOptions) {
        Validator.ensureNotNull(decodeOptions);
        this.decodeOptions = decodeOptions;
        return this;
    }

    /**
     * Sets the maximum request size in bytes for incoming LDAP requests. If an
     * incoming request exceeds the limit then the connection will be aborted by
     * the listener. If the limit is less than {@code 1} then a default value of
     * {@code 5MB} will be used.
     *
     * @param maxRequestSize
     *            The maximum request size in bytes for incoming LDAP requests.
     * @return A reference to this LDAP listener options.
     */
    public LDAPListenerOptions setMaxRequestSize(final int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
        return this;
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
     * @return The class loader which will be used to load the transport
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
     *            The class loader which will be used load the transport
     *            provider, or {@code null} if the default class loader should
     *            be used.
     * @return A reference to this LDAP listener options.
     */
    public final LDAPListenerOptions setProviderClassLoader(ClassLoader classLoader) {
        this.providerClassLoader = classLoader;
        return this;
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
     * @return A reference to this LDAP listener options.
     */
    public LDAPListenerOptions setTransportProvider(String providerName) {
        this.transportProvider = providerName;
        return this;
    }

}
