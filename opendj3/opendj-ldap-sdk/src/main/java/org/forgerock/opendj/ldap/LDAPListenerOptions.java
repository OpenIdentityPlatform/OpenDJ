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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import com.forgerock.opendj.util.Validator;

/**
 * Common options for LDAP listeners.
 */
public final class LDAPListenerOptions {

    private DecodeOptions decodeOptions;
    private int backlog;
    private TCPNIOTransport transport;

    /**
     * Creates a new set of listener options with default settings. SSL will not
     * be enabled, and a default set of decode options will be used.
     */
    public LDAPListenerOptions() {
        this.backlog = 0;
        this.decodeOptions = new DecodeOptions();
        this.transport = null;
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
        this.decodeOptions = new DecodeOptions(options.decodeOptions);
        this.transport = options.transport;
    }

    /**
     * Returns the maximum queue length for incoming connections requests. If a
     * connection request arrives when the queue is full, the connection is
     * refused. If the backlog is less than {@code 1} then a default value of
     * {@code 50} will be used.
     *
     * @return The maximum queue length for incoming connections requests.
     */
    public final int getBacklog() {
        return backlog;
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
     * Returns the Grizzly TCP transport which will be used when initiating
     * connections with the Directory Server.
     * <p>
     * By default this method will return {@code null} indicating that the
     * default transport factory should be used to obtain a TCP transport.
     *
     * @return The Grizzly TCP transport which will be used when initiating
     *         connections with the Directory Server, or {@code null} if the
     *         default transport factory should be used to obtain a TCP
     *         transport.
     */
    public final TCPNIOTransport getTCPNIOTransport() {
        return transport;
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
    public final LDAPListenerOptions setBacklog(final int backlog) {
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
    public final LDAPListenerOptions setDecodeOptions(final DecodeOptions decodeOptions) {
        Validator.ensureNotNull(decodeOptions);
        this.decodeOptions = decodeOptions;
        return this;
    }

    /**
     * Sets the Grizzly TCP transport which will be used when initiating
     * connections with the Directory Server.
     * <p>
     * By default this method will return {@code null} indicating that the
     * default transport factory should be used to obtain a TCP transport.
     *
     * @param transport
     *            The Grizzly TCP transport which will be used when initiating
     *            connections with the Directory Server, or {@code null} if the
     *            default transport factory should be used to obtain a TCP
     *            transport.
     * @return A reference to this connection options.
     */
    public final LDAPListenerOptions setTCPNIOTransport(final TCPNIOTransport transport) {
        this.transport = transport;
        return this;
    }

}
