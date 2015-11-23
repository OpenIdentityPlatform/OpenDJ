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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.util.StaticUtils.*;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;

/**
 * An LDAP server connection listener which waits for LDAP connection requests
 * to come in over the network and binds them to a {@link ServerConnection}
 * created using the provided {@link ServerConnectionFactory}.
 * <p>
 * When processing requests, {@code ServerConnection} implementations are passed
 * an integer as the first parameter. This integer represents the
 * {@code requestID} associated with the client request and corresponds to the
 * {@code requestID} passed as a parameter to abandon and cancel extended
 * requests. The request ID may also be useful for logging purposes.
 * <p>
 * An {@code LDAPListener} does not require {@code ServerConnection}
 * implementations to return a result when processing requests. More
 * specifically, an {@code LDAPListener} does not maintain any internal state
 * information associated with each request which must be released. This is
 * useful when implementing LDAP abandon operations which may prevent results
 * being sent for abandoned operations.
 * <p>
 * The following code illustrates how to create a simple LDAP server:
 *
 * <pre>
 * class MyClientConnection implements ServerConnection&lt;Integer&gt; {
 *     private final LDAPClientContext clientContext;
 *
 *     private MyClientConnection(LDAPClientContext clientContext) {
 *         this.clientContext = clientContext;
 *     }
 *
 *     public void add(Integer requestID, AddRequest request, ResultHandler&lt;Result&gt; handler,
 *             IntermediateResponseHandler intermediateResponseHandler)
 *             throws UnsupportedOperationException {
 *         // ...
 *     }
 *
 *     // ...
 *
 * }
 *
 * class MyServer implements ServerConnectionFactory&lt;LDAPClientContext, RequestContext&gt; {
 *     public ServerConnection&lt;RequestContext&gt; accept(LDAPClientContext context) {
 *         System.out.println(&quot;Connection from: &quot; + context.getPeerAddress());
 *         return new MyClientConnection(context);
 *     }
 * }
 *
 * public static void main(String[] args) throws Exception {
 *     LDAPListener listener = new LDAPListener(1389, new MyServer());
 *
 *     // ...
 *
 *     listener.close();
 * }
 * </pre>
 */
public final class LDAPListener extends CommonLDAPOptions implements Closeable {

    /**
     * Specifies the maximum queue length for incoming connections requests. If a
     * connection request arrives when the queue is full, the connection is refused.
     */
    public static final Option<Integer> CONNECT_MAX_BACKLOG = Option.withDefault(50);

    /**
     * Specifies the maximum request size in bytes for incoming LDAP requests.
     * If an incoming request exceeds the limit then the connection will be aborted by the listener.
     * Default value is 5MiB.
     */
    public static final Option<Integer> REQUEST_MAX_SIZE_IN_BYTES = Option.withDefault(5 * 1024 * 1024);

    /**
     * We implement the factory using the pimpl idiom in order have
     * cleaner Javadoc which does not expose implementation methods.
     */
    private final LDAPListenerImpl impl;

    /**
     * Transport provider that provides the implementation of this listener.
     */
    private TransportProvider provider;

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param port
     *            The port to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {code factory} was {@code null}.
     */
    public LDAPListener(final int port,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory) throws IOException {
        this(port, factory, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param port
     *            The port to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {code factory} or {@code options} was {@code null}.
     */
    public LDAPListener(final int port,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final Options options) throws IOException {
        Reject.ifNull(factory, options);
        this.provider = getTransportProvider(options);
        this.impl = provider.getLDAPListener(new InetSocketAddress(port), factory, options);
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code address} or {code factory} was {@code null}.
     */
    public LDAPListener(final InetSocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory) throws IOException {
        this(address, factory, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param address
     *            The address to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code address}, {code factory}, or {@code options} was
     *             {@code null}.
     */
    public LDAPListener(final InetSocketAddress address,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final Options options) throws IOException {
        Reject.ifNull(address, factory, options);
        this.provider = getTransportProvider(options);
        this.impl = provider.getLDAPListener(address, factory, options);
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param host
     *            The address to listen on.
     * @param port
     *            The port to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code host} or {code factory} was {@code null}.
     */
    public LDAPListener(final String host, final int port,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory) throws IOException {
        this(host, port, factory, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param host
     *            The address to listen on.
     * @param port
     *            The port to listen on.
     * @param factory
     *            The server connection factory which will be used to create
     *            server connections.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code host}, {code factory}, or {@code options} was
     *             {@code null}.
     */
    public LDAPListener(final String host, final int port,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final Options options) throws IOException {
        Reject.ifNull(host, factory, options);
        final InetSocketAddress address = new InetSocketAddress(host, port);
        this.provider = getTransportProvider(options);
        this.impl = provider.getLDAPListener(address, factory, options);
    }

    /**
     * Closes this LDAP connection listener.
     */
    @Override
    public void close() {
        impl.close();
    }

    /**
     * Returns the {@code InetAddress} that this LDAP listener is listening on.
     *
     * @return The {@code InetAddress} that this LDAP listener is listening on.
     */
    public InetAddress getAddress() {
        return getSocketAddress().getAddress();
    }

    /**
     * Returns the host name that this LDAP listener is listening on. The
     * returned host name is the same host name that was provided during
     * construction and may be an IP address. More specifically, this method
     * will not perform a reverse DNS lookup.
     *
     * @return The host name that this LDAP listener is listening on.
     */
    public String getHostName() {
        return Connections.getHostString(getSocketAddress());
    }

    /**
     * Returns the port that this LDAP listener is listening on.
     *
     * @return The port that this LDAP listener is listening on.
     */
    public int getPort() {
        return getSocketAddress().getPort();
    }

    /**
     * Returns the address that this LDAP listener is listening on.
     *
     * @return The address that this LDAP listener is listening on.
     */
    public InetSocketAddress getSocketAddress() {
        return impl.getSocketAddress();
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
