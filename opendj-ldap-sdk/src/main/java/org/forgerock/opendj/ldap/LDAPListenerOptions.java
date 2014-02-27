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

import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

/**
 * Common options for LDAP listeners.
 */
public final class LDAPListenerOptions extends CommonLDAPOptions<LDAPListenerOptions> {
    private int backlog;
    private int maxRequestSize;

    /**
     * Creates a new set of listener options with default settings. SSL will not
     * be enabled, and a default set of decode options will be used.
     */
    public LDAPListenerOptions() {
        super();
    }

    /**
     * Creates a new set of listener options having the same initial set of
     * options as the provided set of listener options.
     *
     * @param options
     *            The set of listener options to be copied.
     */
    public LDAPListenerOptions(final LDAPListenerOptions options) {
        super(options);
        this.backlog = options.backlog;
        this.maxRequestSize = options.maxRequestSize;
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
     * {@inheritDoc}
     */
    @Override
    public LDAPListenerOptions setDecodeOptions(DecodeOptions decodeOptions) {
        // This method is required for binary compatibility.
        return super.setDecodeOptions(decodeOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LDAPListenerOptions setTCPNIOTransport(TCPNIOTransport transport) {
        // This method is required for binary compatibility.
        return super.setTCPNIOTransport(transport);
    }

    @Override
    LDAPListenerOptions getThis() {
        return this;
    }
}
