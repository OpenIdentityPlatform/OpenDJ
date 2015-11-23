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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Options;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.ssl.SSLFilter;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;

/**
 * Common utility methods.
 */
final class GrizzlyUtils {
    @SuppressWarnings("rawtypes")
    private static final ThreadCache.CachedTypeIndex<LDAPWriter> WRITER_INDEX = ThreadCache
            .obtainIndex(LDAPWriter.class, 1);

    /**
     * Build a filter chain from the provided processor if possible and the
     * provided filter.
     * <p>
     * If the provided processor can't be used for building the new filter
     * chain, then a chain with only a {@code TransportFilter} is used as a base
     * chain.
     *
     * @param processor
     *            processor to build the filter chain from. If the processor is
     *            not a filter chain (for example, it can be a
     *            {@code StandaloneProcessor} then it is ignored to build the
     *            returned filter chain
     * @param filter
     *            filter to add at the end of the filter chain
     * @return a new filter chain, based on the provided processor if processor
     *         is a {@code FilterChain}, and having the provided filter as the
     *         last filter
     */
    static FilterChain buildFilterChain(Processor<?> processor, Filter filter) {
        if (processor instanceof FilterChain) {
            return FilterChainBuilder.stateless().addAll((FilterChain) processor).add(filter).build();
        } else {
            return FilterChainBuilder.stateless().add(new TransportFilter()).add(filter).build();
        }
    }

    /**
     * Adds a filter to filter chain registered with the given connection.
     * <p>
     * For a non-SSL filter, filter is added at the last position before the
     * LDAP filter.
     * <p>
     * For a SSL filter, filter is added before any
     * {@code ConnectionSecurityLayerFilter} which is already present in the
     * filter chain.
     *
     * @param filter
     *            filter to add
     * @param connection
     *            connection to update with the new filter chain containing the
     *            provided filter
     */
    static void addFilterToConnection(final Filter filter, Connection<?> connection) {
        final FilterChain currentChain = (FilterChain) connection.getProcessor();
        final FilterChain newChain = addFilterToChain(filter, currentChain);
        connection.setProcessor(newChain);
    }

    /**
     * Adds a filter to a filter chain.
     * <p>
     * For a non-SSL filter, filter is added at the last position before the
     * LDAP filter.
     * <p>
     * For a SSL filter, filter is added before any
     * {@code ConnectionSecurityLayerFilter} which is already present in the
     * filter chain.
     *
     * @param filter
     *            filter to add
     * @param chain
     *            initial filter chain
     * @return a new filter chain which includes the provided filter
     */
    static FilterChain addFilterToChain(final Filter filter, final FilterChain chain) {
        // By default, before LDAP filter which is the last one
        int indexToAddFilter = chain.size() - 1;
        if (filter instanceof SSLFilter) {
            // Before any ConnectionSecurityLayerFilters if present
            for (int i = chain.size() - 2; i >= 0; i--) {
                if (!(chain.get(i) instanceof ConnectionSecurityLayerFilter)) {
                    indexToAddFilter = i + 1;
                    break;
                }
            }
        }
        return FilterChainBuilder.stateless().addAll(chain).add(indexToAddFilter, filter).build();
    }

    /**
     * Creates a new LDAP Reader with the provided maximum size of ASN1 element,
     * options and memory manager.
     *
     * @param decodeOptions
     *            allow to control how responses and requests are decoded
     * @param maxASN1ElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     * @param memoryManager
     *            The memory manager to use for buffering.
     * @return a LDAP reader
     */
    static LDAPReader<ASN1BufferReader> createReader(DecodeOptions decodeOptions,
            int maxASN1ElementSize, MemoryManager<?> memoryManager) {
        ASN1BufferReader asn1Reader = new ASN1BufferReader(maxASN1ElementSize, memoryManager);
        return LDAP.getReader(asn1Reader, decodeOptions);
    }

    /**
     * Returns a LDAP writer, with a clean ASN1Writer, possibly from
     * the thread local cache.
     * <p>
     * The writer is either returned from thread local cache or created.
     * In the former case, the writer is removed from the cache.
     *
     * @return a LDAP writer
     */
    @SuppressWarnings("unchecked")
    static LDAPWriter<ASN1BufferWriter> getWriter() {
        LDAPWriter<ASN1BufferWriter> writer = ThreadCache.takeFromCache(WRITER_INDEX);
        if (writer == null) {
            writer = LDAP.getWriter(new ASN1BufferWriter());
        }
        writer.getASN1Writer().reset();
        return writer;
    }

    /**
     * Recycle a LDAP writer to a thread local cache.
     * <p>
     * The LDAP writer is then available for the thread using the
     * {@get()} method.
     *
     * @param writer LDAP writer to recycle
     */
    static void recycleWriter(LDAPWriter<ASN1BufferWriter> writer) {
        writer.getASN1Writer().recycle();
        ThreadCache.putToCache(WRITER_INDEX, writer);
    }

    static void configureConnection(final Connection<?> connection, final LocalizedLogger logger, Options options) {
        /*
         * Test shows that its much faster with non block writes but risk
         * running out of memory if the server is slow.
         */
        connection.configureBlocking(true);

        // Configure socket options.
        final SocketChannel channel = (SocketChannel) ((TCPNIOConnection) connection).getChannel();
        final Socket socket = channel.socket();
        final boolean tcpNoDelay = options.get(TCP_NO_DELAY);
        final boolean keepAlive = options.get(SO_KEEPALIVE);
        final boolean reuseAddress = options.get(SO_REUSE_ADDRESS);
        final int linger = options.get(SO_LINGER_IN_SECONDS);
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (final SocketException e) {
            logger.traceException(e, "Unable to set TCP_NODELAY to %d on client connection",
                    tcpNoDelay);
        }
        try {
            socket.setKeepAlive(keepAlive);
        } catch (final SocketException e) {
            logger.traceException(e, "Unable to set SO_KEEPALIVE to %d on client connection",
                    keepAlive);
        }
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (final SocketException e) {
            logger.traceException(e, "Unable to set SO_REUSEADDR to %d on client connection",
                    reuseAddress);
        }
        try {
            if (linger < 0) {
                socket.setSoLinger(false, 0);
            } else {
                socket.setSoLinger(true, linger);
            }
        } catch (final SocketException e) {
            logger.traceException(e, "Unable to set SO_LINGER to %d on client connection", linger);
        }
    }

    /** Prevent instantiation. */
    private GrizzlyUtils() {
        // No implementation required.
    }

}
