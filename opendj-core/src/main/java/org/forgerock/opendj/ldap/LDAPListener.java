/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;

import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Function;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;

import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Stream;

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
     * Specifies the maximum number of concurrent requests per connection. Once this number is reached,
     * back-pressure mechanism will stop reading requests from the connection.
     */
    public static final Option<Integer> MAX_CONCURRENT_REQUESTS = Option.withDefault(1024);

    /**
     * We implement the factory using the pimpl idiom in order have
     * cleaner Javadoc which does not expose implementation methods.
     */
    private final LDAPListenerImpl impl;

    /** Transport provider that provides the implementation of this listener. */
    private TransportProvider provider;

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param port
     *            The port to listen on.
     * @param factory
     *            The handler factory which will be used to create handlers.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {code factory} was {@code null}.
     */
    public LDAPListener(final int port,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory) throws IOException {
        this(port, factory, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param port
     *            The port to listen on.
     * @param factory
     *            The handler factory which will be used to create handlers.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {code factory} or {@code options} was {@code null}.
     */
    public LDAPListener(final int port,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory,
            final Options options) throws IOException {
        this(Collections.singleton(new InetSocketAddress(port)), factory, options);
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param addresses
     *            The addresses to listen on.
     * @param factory
     *            The handler factory which will be used to create handlers.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code address} or {code factory} was {@code null}.
     */
    public LDAPListener(final Set<InetSocketAddress> addresses,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory) throws IOException {
        this(addresses, factory, Options.defaultOptions());
    }

    /**
     * Creates a new LDAP listener implementation which will listen for LDAP
     * client connections at the provided address.
     *
     * @param addresses
     *            The addresses to listen on.
     * @param factory
     *            The handler factory which will be used to create handlers.
     * @param options
     *            The LDAP listener options.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code address}, {code factory}, or {@code options} was
     *             {@code null}.
     */
    public LDAPListener(final Set<InetSocketAddress> addresses,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory,
            final Options options) throws IOException {
        Reject.ifNull(addresses, factory, options);
        this.provider = getTransportProvider(options);
        this.impl = provider.getLDAPListener(addresses, factory, options);
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
     *            The handler factory which will be used to create handlers.
     * @throws IOException
     *             If an error occurred while trying to listen on the provided
     *             address.
     * @throws NullPointerException
     *             If {@code host} or {code factory} was {@code null}.
     */
    public LDAPListener(final String host, final int port,
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory) throws IOException {
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
     *            The handler factory which will be used to create handlers.
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
            final Function<LDAPClientContext,
                           ReactiveHandler<LDAPClientContext, LdapRequestEnvelope, Stream<Response>>,
                           LdapException> factory,
            final Options options) throws IOException {
        this(Collections.singleton(new InetSocketAddress(host, port)), factory, options);
    }

    /** Closes this LDAP connection listener. */
    @Override
    public void close() {
        impl.close();
    }

    /**
     * Returns the addresses that this LDAP listener is listening on.
     *
     * @return The addresses that this LDAP listener is listening on.
     */
    public Set<InetSocketAddress> getSocketAddresses() {
        return impl.getSocketAddresses();
    }

    /**
     * Returns the first address that his LDAP listener is listening on.
     *
     * @return The addresses that this LDAP listener is listening on.
     */
    public InetSocketAddress firstSocketAddress() {
        return impl.getSocketAddresses().iterator().next();
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
