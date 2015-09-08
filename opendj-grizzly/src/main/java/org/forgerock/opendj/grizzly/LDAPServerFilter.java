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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.grizzly;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.AbstractLDAPMessageHandler;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLUtils;

import static org.forgerock.opendj.grizzly.GrizzlyUtils.*;

/**
 * Grizzly filter implementation for decoding LDAP requests and handling server
 * side logic for SSL and SASL operations over LDAP.
 */
final class LDAPServerFilter extends LDAPBaseFilter {

    /**
     * Provides an arbitrary write operation on a LDAP writer.
     */
    private interface LDAPWrite<T> {
        void perform(LDAPWriter<ASN1BufferWriter> writer, int messageID, T message)
                throws IOException;
    }

    /**
     * Write operation for intermediate responses.
     */
    private static final LDAPWrite<IntermediateResponse> INTERMEDIATE =
            new LDAPWrite<IntermediateResponse>() {
                @Override
                public void perform(LDAPWriter<ASN1BufferWriter> writer, int messageID,
                        IntermediateResponse resp) throws IOException {
                    writer.writeIntermediateResponse(messageID, resp);
                }
            };

    private static abstract class AbstractHandler<R extends Result> implements
            IntermediateResponseHandler, LdapResultHandler<R> {
        protected final ClientContextImpl context;
        protected final int messageID;

        protected AbstractHandler(final ClientContextImpl context, final int messageID) {
            this.messageID = messageID;
            this.context = context;
        }

        @Override
        public void handleResult(final R result) {
            defaultHandleResult(result);
        }

        @Override
        public final boolean handleIntermediateResponse(final IntermediateResponse response) {
            writeMessage(INTERMEDIATE, response);
            return true;
        }

        /**
         * Default implementation of result handling, that delegate the actual
         * write operation to {@code writeResult} method.
         */
        private void defaultHandleResult(final R result) {
            writeMessage(new LDAPWrite<R>() {
                @Override
                public void perform(LDAPWriter<ASN1BufferWriter> writer, int messageID, R res)
                        throws IOException {
                    writeResult(writer, res);
                }
            }, result);
        }

        /**
         * Write a result to provided LDAP writer.
         *
         * @param ldapWriter
         *            provided writer
         * @param result
         *            to write
         * @throws IOException
         *             if an error occurs during writing
         */
        protected abstract void writeResult(final LDAPWriter<ASN1BufferWriter> ldapWriter,
                final R result) throws IOException;

        /**
         * Write a message on LDAP writer.
         *
         * @param <T>
         *            type of message to write
         * @param ldapWrite
         *            the specific write operation
         * @param message
         *            the message to write
         */
        protected final <T> void writeMessage(final LDAPWrite<T> ldapWrite, final T message) {
            final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
            try {
                ldapWrite.perform(writer, messageID, message);
                context.write(writer);
            } catch (final IOException ioe) {
                context.handleException(ioe);
            } finally {
                GrizzlyUtils.recycleWriter(writer);
            }
        }

        /**
         * Copy diagnostic message, matched DN and cause to new result from the
         * given result.
         *
         * @param newResult
         *            to update
         * @param result
         *            contains parameters to copy
         */
        protected final void populateNewResultFromResult(final R newResult, final Result result) {
            newResult.setDiagnosticMessage(result.getDiagnosticMessage());
            newResult.setMatchedDN(result.getMatchedDN());
            newResult.setCause(result.getCause());
            for (final Control control : result.getControls()) {
                newResult.addControl(control);
            }
        }
    }

    private static final class AddHandler extends AbstractHandler<Result> {
        private AddHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            handleResult(error.getResult());
        }

        @Override
        public void writeResult(LDAPWriter<ASN1BufferWriter> writer, final Result result)
                throws IOException {
            writer.writeAddResult(messageID, result);
        }
    }

    private static final class BindHandler extends AbstractHandler<BindResult> {
        private BindHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            final Result result = error.getResult();
            if (result instanceof BindResult) {
                handleResult((BindResult) result);
            } else {
                final BindResult newResult = Responses.newBindResult(result.getResultCode());
                populateNewResultFromResult(newResult, result);
                handleResult(newResult);
            }
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> writer, BindResult result)
                throws IOException {
            writer.writeBindResult(messageID, result);
        }
    }

    private static final class ClientContextImpl implements LDAPClientContext {
        private final Connection<?> connection;
        private final AtomicBoolean isClosed = new AtomicBoolean();
        private ServerConnection<Integer> serverConnection;

        private ClientContextImpl(final Connection<?> connection) {
            this.connection = connection;
        }

        @Override
        public void disconnect() {
            disconnect0(null, null);
        }

        @Override
        public void disconnect(final ResultCode resultCode, final String message) {
            Reject.ifNull(resultCode);
            final GenericExtendedResult notification =
                    Responses.newGenericExtendedResult(resultCode).setOID(
                            LDAP.OID_NOTICE_OF_DISCONNECTION).setDiagnosticMessage(message);
            sendUnsolicitedNotification(notification);
            disconnect0(resultCode, message);
        }

        @Override
        public void enableConnectionSecurityLayer(final ConnectionSecurityLayer layer) {
            synchronized (this) {
                installFilter(new ConnectionSecurityLayerFilter(layer, connection.getTransport()
                        .getMemoryManager()));
            }
        }

        @Override
        public void enableTLS(final SSLContext sslContext, final String[] protocols,
                final String[] suites, final boolean wantClientAuth, final boolean needClientAuth) {
            Reject.ifNull(sslContext);
            synchronized (this) {
                if (isTLSEnabled()) {
                    throw new IllegalStateException("TLS already enabled");
                }

                final SSLEngineConfigurator sslEngineConfigurator =
                        new SSLEngineConfigurator(sslContext, false, false, false);
                sslEngineConfigurator.setEnabledCipherSuites(suites);
                sslEngineConfigurator.setEnabledProtocols(protocols);
                sslEngineConfigurator.setWantClientAuth(wantClientAuth);
                sslEngineConfigurator.setNeedClientAuth(needClientAuth);
                installFilter(new SSLFilter(sslEngineConfigurator, DUMMY_SSL_ENGINE_CONFIGURATOR));
            }
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return (InetSocketAddress) connection.getLocalAddress();
        }

        @Override
        public InetSocketAddress getPeerAddress() {
            return (InetSocketAddress) connection.getPeerAddress();
        }

        @Override
        public int getSecurityStrengthFactor() {
            final SSLSession sslSession = getSSLSession();
            if (sslSession != null) {
                final String cipherString = sslSession.getCipherSuite();
                for (final Object[] cipher : CIPHER_KEY_SIZES) {
                    if (cipherString.contains((String) cipher[0])) {
                        return (Integer) cipher[1];
                    }
                }
            }
            return 0;
        }

        @Override
        public SSLSession getSSLSession() {
            final SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
            return sslEngine != null ? sslEngine.getSession() : null;
        }

        @Override
        public boolean isClosed() {
            return isClosed.get();
        }

        @Override
        public void sendUnsolicitedNotification(final ExtendedResult notification) {
            LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
            try {
                writer.writeExtendedResult(0, notification);
                connection.write(writer.getASN1Writer().getBuffer(), null);
            } catch (final IOException ioe) {
                handleException(ioe);
            } finally {
                GrizzlyUtils.recycleWriter(writer);
            }
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("LDAPClientContext(");
            builder.append(getLocalAddress());
            builder.append(',');
            builder.append(getPeerAddress());
            builder.append(')');
            return builder.toString();
        }

        public void write(final LDAPWriter<ASN1BufferWriter> writer) {
            connection.write(writer.getASN1Writer().getBuffer(), null);
        }

        private void disconnect0(final ResultCode resultCode, final String message) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                try {
                    // Notify the server connection: it may be null if disconnect is
                    // invoked during accept.
                    if (serverConnection != null) {
                        serverConnection.handleConnectionDisconnected(resultCode, message);
                    }
                } finally {
                    // Close the connection.
                    connection.closeSilently();
                }
            }
        }

        private ServerConnection<Integer> getServerConnection() {
            return serverConnection;
        }

        private void handleClose(final int messageID, final UnbindRequest unbindRequest) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                try {
                    // Notify the server connection: it may be null if disconnect is
                    // invoked during accept.
                    if (serverConnection != null) {
                        serverConnection.handleConnectionClosed(messageID, unbindRequest);
                    }
                } finally {
                    // If this close was a result of an unbind request then the
                    // connection won't actually be closed yet. To avoid TIME_WAIT TCP
                    // state, let the client disconnect.
                    if (unbindRequest != null) {
                        return;
                    }

                    // Close the connection.
                    connection.closeSilently();
                }
            }
        }

        private void handleException(final Throwable error) {
            // Close this connection context.
            if (isClosed.compareAndSet(false, true)) {
                try {
                    // Notify the server connection: it may be null if disconnect is
                    // invoked during accept.
                    if (serverConnection != null) {
                        serverConnection.handleConnectionError(error);
                    }
                } finally {
                    // Close the connection.
                    connection.closeSilently();
                }
            }
        }

        /**
         * Installs a new Grizzly filter (e.g. SSL/SASL) beneath the top-level
         * LDAP filter.
         *
         * @param filter
         *            The filter to be installed.
         */
        private void installFilter(final Filter filter) {
            GrizzlyUtils.addFilterToConnection(filter, connection);
        }

        /**
         * Indicates whether or not TLS is enabled this provided connection.
         *
         * @return {@code true} if TLS is enabled on this connection, otherwise
         *         {@code false}.
         */
        private boolean isTLSEnabled() {
            synchronized (this) {
                final FilterChain currentFilterChain = (FilterChain) connection.getProcessor();
                for (final Filter filter : currentFilterChain) {
                    if (filter instanceof SSLFilter) {
                        return true;
                    }
                }
                return false;
            }
        }

        private void setServerConnection(final ServerConnection<Integer> serverConnection) {
            this.serverConnection = serverConnection;
        }
    }

    private static final class CompareHandler extends AbstractHandler<CompareResult> {
        private CompareHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            final Result result = error.getResult();
            if (result instanceof CompareResult) {
                handleResult((CompareResult) result);
            } else {
                final CompareResult newResult = Responses.newCompareResult(result.getResultCode());
                populateNewResultFromResult(newResult, result);
                handleResult(newResult);
            }
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> writer, CompareResult result)
                throws IOException {
            writer.writeCompareResult(messageID, result);

        }
    }

    private static final class DeleteHandler extends AbstractHandler<Result> {
        private DeleteHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            handleResult(error.getResult());
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> writer, Result result)
                throws IOException {
            writer.writeDeleteResult(messageID, result);
        }
    }

    private static final class ExtendedHandler<R extends ExtendedResult> extends AbstractHandler<R> {
        private ExtendedHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            final Result result = error.getResult();
            if (result instanceof ExtendedResult) {
                handleResult((ExtendedResult) result);
            } else {
                final ExtendedResult newResult =
                        Responses.newGenericExtendedResult(result.getResultCode());
                newResult.setDiagnosticMessage(result.getDiagnosticMessage());
                newResult.setMatchedDN(result.getMatchedDN());
                newResult.setCause(result.getCause());
                for (final Control control : result.getControls()) {
                    newResult.addControl(control);
                }
                handleResult(newResult);
            }
        }

        @Override
        public void handleResult(final ExtendedResult result) {
            writeMessage(new LDAPWrite<ExtendedResult>() {
                @Override
                public void perform(LDAPWriter<ASN1BufferWriter> writer, int messageID,
                        ExtendedResult message) throws IOException {
                    writer.writeExtendedResult(messageID, message);
                }
            }, result);
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> ldapWriter, R result)
                throws IOException {
            // never called because handleResult(result) method is overriden in this class
        }
    }

    private static final class ModifyDNHandler extends AbstractHandler<Result> {
        private ModifyDNHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            handleResult(error.getResult());
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> writer, Result result)
                throws IOException {
            writer.writeModifyDNResult(messageID, result);
        }
    }

    private static final class ModifyHandler extends AbstractHandler<Result> {
        private ModifyHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public void handleException(final LdapException error) {
            handleResult(error.getResult());
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> writer, Result result)
                throws IOException {
            writer.writeModifyResult(messageID, result);
        }
    }

    private static final class SearchHandler extends AbstractHandler<Result> implements
            SearchResultHandler {
        private SearchHandler(final ClientContextImpl context, final int messageID) {
            super(context, messageID);
        }

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            writeMessage(new LDAPWrite<SearchResultEntry>() {
                @Override
                public void perform(LDAPWriter<ASN1BufferWriter> writer, int messageID,
                        SearchResultEntry sre) throws IOException {
                    writer.writeSearchResultEntry(messageID, sre);
                }
            }, entry);
            return true;
        }

        @Override
        public void handleException(final LdapException error) {
            handleResult(error.getResult());
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            writeMessage(new LDAPWrite<SearchResultReference>() {
                @Override
                public void perform(LDAPWriter<ASN1BufferWriter> writer, int messageID,
                        SearchResultReference ref) throws IOException {
                    writer.writeSearchResultReference(messageID, ref);
                }
            }, reference);
            return true;
        }

        @Override
        protected void writeResult(LDAPWriter<ASN1BufferWriter> writer, Result result)
                throws IOException {
            writer.writeSearchResult(messageID, result);
        }
    }
    // @formatter:off

    /**
     * Map of cipher phrases to effective key size (bits). Taken from the
     * following RFCs: 5289, 4346, 3268,4132 and 4162.
     */
    private static final Object[][] CIPHER_KEY_SIZES = {
        { "_WITH_AES_256_CBC_",      256 },
        { "_WITH_CAMELLIA_256_CBC_", 256 },
        { "_WITH_AES_256_GCM_",      256 },
        { "_WITH_3DES_EDE_CBC_",     112 },
        { "_WITH_AES_128_GCM_",      128 },
        { "_WITH_SEED_CBC_",         128 },
        { "_WITH_CAMELLIA_128_CBC_", 128 },
        { "_WITH_AES_128_CBC_",      128 },
        { "_WITH_IDEA_CBC_",         128 },
        { "_WITH_RC4_128_",          128 },
        { "_WITH_FORTEZZA_CBC_",     96 },
        { "_WITH_DES_CBC_",          56 },
        { "_WITH_RC4_56_",           56 },
        { "_WITH_RC2_CBC_40_",       40 },
        { "_WITH_DES_CBC_40_",       40 },
        { "_WITH_RC4_40_",           40 },
        { "_WITH_DES40_CBC_",        40 },
        { "_WITH_NULL_",             0 },
    };

    // @formatter:on
    /**
     * Default maximum request size for incoming requests.
     */
    private static final int DEFAULT_MAX_REQUEST_SIZE = 5 * 1024 * 1024;

    private static final Attribute<ClientContextImpl> LDAP_CONNECTION_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("LDAPServerConnection");

    private static final Attribute<ServerRequestHandler> REQUEST_HANDLER_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("ServerRequestHandler");

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * A dummy SSL client engine configurator as SSLFilter only needs server
     * config. This prevents Grizzly from needlessly using JVM defaults which
     * may be incorrectly configured.
     */
    private static final SSLEngineConfigurator DUMMY_SSL_ENGINE_CONFIGURATOR;
    static {
        try {
            DUMMY_SSL_ENGINE_CONFIGURATOR =
                    new SSLEngineConfigurator(new SSLContextBuilder().setTrustManager(
                            TrustManagers.distrustAll()).getSSLContext());
        } catch (GeneralSecurityException e) {
            // This should never happen.
            throw new IllegalStateException("Unable to create Dummy SSL Engine Configurator", e);
        }
    }

    private final GrizzlyLDAPListener listener;

    private static final class ServerRequestHandler extends AbstractLDAPMessageHandler implements
            LDAPBaseHandler {

        private final Connection<?> connection;
        private final LDAPReader<ASN1BufferReader> reader;

        /**
         * Creates the handler with a connection.
         *
         * @param connection
         *            connection this handler is associated with
         * @param reader
         *            LDAP reader to use for reading incoming messages
         */
        ServerRequestHandler(Connection<?> connection, LDAPReader<ASN1BufferReader> reader) {
            this.connection = connection;
            this.reader = reader;
        }

        /**
         * Returns the LDAP reader.
         *
         * @return the reader to read incoming LDAP messages
         */
        @Override
        public LDAPReader<ASN1BufferReader> getReader() {
            return reader;
        }

        @Override
        public void abandonRequest(final int messageID, final AbandonRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                conn.handleAbandon(messageID, request);
            }
        }

        @Override
        public void addRequest(final int messageID, final AddRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final AddHandler handler = new AddHandler(clientContext, messageID);
                conn.handleAdd(messageID, request, handler, handler);
            }
        }

        @Override
        public void bindRequest(final int messageID, final int version,
                final GenericBindRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final AbstractHandler<BindResult> handler =
                        new BindHandler(clientContext, messageID);
                conn.handleBind(messageID, version, request, handler, handler);
            }
        }

        @Override
        public void compareRequest(final int messageID, final CompareRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final CompareHandler handler = new CompareHandler(clientContext, messageID);
                conn.handleCompare(messageID, request, handler, handler);
            }
        }

        @Override
        public void deleteRequest(final int messageID, final DeleteRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final DeleteHandler handler = new DeleteHandler(clientContext, messageID);
                conn.handleDelete(messageID, request, handler, handler);
            }
        }

        @Override
        public <R extends ExtendedResult> void extendedRequest(final int messageID,
                final ExtendedRequest<R> request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final ExtendedHandler<R> handler = new ExtendedHandler<>(clientContext, messageID);
                conn.handleExtendedRequest(messageID, request, handler, handler);
            }
        }

        @Override
        public void modifyDNRequest(final int messageID, final ModifyDNRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final ModifyDNHandler handler = new ModifyDNHandler(clientContext, messageID);
                conn.handleModifyDN(messageID, request, handler, handler);
            }
        }

        @Override
        public void modifyRequest(final int messageID, final ModifyRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final ModifyHandler handler = new ModifyHandler(clientContext, messageID);
                conn.handleModify(messageID, request, handler, handler);
            }
        }

        @Override
        public void searchRequest(final int messageID, final SearchRequest request) {
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.get(connection);
            if (clientContext != null) {
                final ServerConnection<Integer> conn = clientContext.getServerConnection();
                final SearchHandler handler = new SearchHandler(clientContext, messageID);
                conn.handleSearch(messageID, request, handler, handler, handler);
            }
        }

        @Override
        public void unbindRequest(final int messageID, final UnbindRequest request) {
            // Remove the client context causing any subsequent LDAP
            // traffic to be ignored.
            final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.remove(connection);
            if (clientContext != null) {
                clientContext.handleClose(messageID, request);
            }
        }

        @Override
        public void unrecognizedMessage(final int messageID, final byte messageTag,
                final ByteString messageBytes) {
            exceptionOccurred(connection, newUnsupportedMessageException(messageID, messageTag,
                    messageBytes));
        }
    }

    /**
     * Creates a server filter with provided listener, options and max size of
     * ASN1 element.
     *
     * @param listener
     *            listen for incoming connections
     * @param options
     *            control how to decode requests and responses
     * @param maxASN1ElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     */
    LDAPServerFilter(final GrizzlyLDAPListener listener, final DecodeOptions options,
            final int maxASN1ElementSize) {
        super(options, maxASN1ElementSize <= 0 ? DEFAULT_MAX_REQUEST_SIZE : maxASN1ElementSize);
        this.listener = listener;
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx, final Throwable error) {
        exceptionOccurred(ctx.getConnection(), error);
    }

    private static void exceptionOccurred(final Connection<?> connection, final Throwable error) {
        final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.remove(connection);
        if (clientContext != null) {
            clientContext.handleException(error);
        }
    }

    @Override
    public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        Options options = listener.getLDAPListenerOptions();
        configureConnection(connection, logger, options);
        try {
            final ClientContextImpl clientContext = new ClientContextImpl(connection);
            final ServerConnection<Integer> serverConn =
                    listener.getConnectionFactory().handleAccept(clientContext);
            clientContext.setServerConnection(serverConn);
            LDAP_CONNECTION_ATTR.set(connection, clientContext);
        } catch (final LdapException e) {
            connection.close();
        }

        return ctx.getStopAction();
    }

    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR.remove(ctx.getConnection());
        if (clientContext != null) {
            clientContext.handleClose(-1, null);
        }
        return ctx.getStopAction();
    }

    @Override
    final void handleReadException(FilterChainContext ctx, IOException e) {
        exceptionOccurred(ctx, e);
    }

    /**
     * Returns the request handler associated to a connection.
     * <p>
     * If no handler exists yet for this context, a new one is created and
     * recorded for the context.
     *
     * @param ctx
     *            Context
     * @return the response handler associated to the context, which can be a
     *         new one if no handler have been created yet
     */
    @Override
    final LDAPBaseHandler getLDAPHandler(final FilterChainContext ctx) {
        Connection<?> connection = ctx.getConnection();
        ServerRequestHandler handler = REQUEST_HANDLER_ATTR.get(connection);
        if (handler == null) {
            LDAPReader<ASN1BufferReader> reader =
                    GrizzlyUtils.createReader(decodeOptions, maxASN1ElementSize, connection
                            .getTransport().getMemoryManager());
            handler = new ServerRequestHandler(connection, reader);
            REQUEST_HANDLER_ATTR.set(connection, handler);
        }
        return handler;
    }
}
