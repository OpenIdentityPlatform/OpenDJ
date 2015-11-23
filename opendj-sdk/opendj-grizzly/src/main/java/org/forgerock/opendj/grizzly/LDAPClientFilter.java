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

import java.io.EOFException;
import java.io.IOException;

import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.io.AbstractLDAPMessageHandler;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.BindResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.ExtendedResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.ResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.SearchResultLdapPromiseImpl;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.responses.Responses.*;

/**
 * Grizzly filter implementation for decoding LDAP responses and handling client
 * side logic for SSL and SASL operations over LDAP.
 */
final class LDAPClientFilter extends LDAPBaseFilter {
    private static final Attribute<GrizzlyLDAPConnection> LDAP_CONNECTION_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("LDAPClientConnection");

    private static final Attribute<ClientResponseHandler> RESPONSE_HANDLER_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("ClientResponseHandler");

    static final class ClientResponseHandler extends AbstractLDAPMessageHandler implements
            LDAPBaseHandler {

        private final LDAPReader<ASN1BufferReader> reader;
        private FilterChainContext context;

        /**
         * Creates a handler with the provided reader.
         *
         * @param reader
         *            LDAP reader to use for reading incoming messages
         */
        ClientResponseHandler(LDAPReader<ASN1BufferReader> reader) {
            this.reader = reader;
        }

        void setFilterChainContext(FilterChainContext context) {
            this.context = context;
        }

        /**
         * Returns the LDAP reader.
         *
         * @return the reader to read incoming LDAP messages
         */
        public LDAPReader<ASN1BufferReader> getReader() {
            return this.reader;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public void addResult(final int messageID, final Result result) throws DecodeException,
                IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest.getRequest() instanceof AddRequest) {
                        pendingRequest.setResultOrError(result);
                        return;
                    }
                    throw newUnexpectedResponseException(messageID, result);
                }
            }
        }

        @Override
        public void bindResult(final int messageID, final BindResult result)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest instanceof BindResultLdapPromiseImpl) {
                        final BindResultLdapPromiseImpl promise = (BindResultLdapPromiseImpl) pendingRequest;
                        final BindClient bindClient = promise.getBindClient();

                        try {
                            if (!bindClient.evaluateResult(result)) {
                                // The server is expecting a multi stage
                                // bind response.
                                final int msgID = ldapConnection.continuePendingBindRequest(promise);

                                LDAPWriter<ASN1BufferWriter> ldapWriter = GrizzlyUtils.getWriter();
                                try {
                                    final GenericBindRequest nextRequest =
                                            bindClient.nextBindRequest();
                                    ldapWriter.writeBindRequest(msgID, 3, nextRequest);
                                    context.write(ldapWriter.getASN1Writer().getBuffer(), null);
                                } finally {
                                    GrizzlyUtils.recycleWriter(ldapWriter);
                                }
                                return;
                            }
                        } catch (final LdapException e) {
                            ldapConnection.setBindOrStartTLSInProgress(false);
                            promise.adaptErrorResult(e.getResult());
                            return;
                        } catch (final IOException e) {
                            // FIXME: I18N need to have a better error message.
                            // FIXME: Is this the best result code?
                            ldapConnection.setBindOrStartTLSInProgress(false);
                            final Result errorResult =
                                    Responses
                                            .newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR)
                                            .setDiagnosticMessage(
                                                    "An error occurred during multi-stage authentication")
                                            .setCause(e);
                            promise.adaptErrorResult(errorResult);
                            return;
                        }

                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            final ConnectionSecurityLayer l =
                                    bindClient.getConnectionSecurityLayer();
                            if (l != null) {
                                // The connection needs to be secured by
                                // the SASL mechanism.
                                ldapConnection.installFilter(new ConnectionSecurityLayerFilter(l,
                                        context.getConnection().getTransport().getMemoryManager()));
                            }
                        }

                        ldapConnection.setBindOrStartTLSInProgress(false);
                        promise.setResultOrError(result);
                        return;
                    }
                    throw newUnexpectedResponseException(messageID, result);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void compareResult(final int messageID, final CompareResult result)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest.getRequest() instanceof CompareRequest) {
                        ((ResultLdapPromiseImpl<CompareRequest, CompareResult>) pendingRequest)
                                .setResultOrError(result);
                        return;
                    }
                    throw newUnexpectedResponseException(messageID, result);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void deleteResult(final int messageID, final Result result) throws DecodeException,
                IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest.getRequest() instanceof DeleteRequest) {
                        ((ResultLdapPromiseImpl<DeleteRequest, Result>) pendingRequest).setResultOrError(result);
                        return;
                    }
                    throw newUnexpectedResponseException(messageID, result);
                }
            }
        }

        @Override
        public void extendedResult(final int messageID, final ExtendedResult result)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                if (messageID == 0) {
                    // Unsolicited notification received.
                    if (LDAP.OID_NOTICE_OF_DISCONNECTION.equals(result.getOID())) {
                        // Treat this as a connection error.
                        final Result errorResult = newResult(result.getResultCode()).setDiagnosticMessage(
                                        result.getDiagnosticMessage());
                        ldapConnection.close(null, true, errorResult);
                    } else {
                        ldapConnection.handleUnsolicitedNotification(result);
                    }
                } else {
                    final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                    if (pendingRequest != null) {
                        if (pendingRequest.getRequest() instanceof ExtendedRequest) {
                            final ExtendedResultLdapPromiseImpl<?> extendedPromise =
                                    (ExtendedResultLdapPromiseImpl<?>) pendingRequest;
                            try {
                                handleExtendedResult0(ldapConnection, extendedPromise, result);
                            } catch (final DecodeException de) {
                                // FIXME: should the connection be closed as well?
                                final Result errorResult = newResult(ResultCode.CLIENT_SIDE_DECODING_ERROR)
                                                .setDiagnosticMessage(de.getLocalizedMessage())
                                                .setCause(de);
                                extendedPromise.adaptErrorResult(errorResult);
                            }
                        } else {
                            throw newUnexpectedResponseException(messageID, result);
                        }
                    }
                }
            }
        }

        @Override
        public void intermediateResponse(final int messageID, final IntermediateResponse response)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.getPendingRequest(messageID);
                if (pendingRequest != null) {
                    pendingRequest.handleIntermediateResponse(response);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void modifyDNResult(final int messageID, final Result result)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest.getRequest() instanceof ModifyDNRequest) {
                        ((ResultLdapPromiseImpl<ModifyDNRequest, Result>) pendingRequest).setResultOrError(result);
                        return;
                    }
                    throw newUnexpectedResponseException(messageID, result);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void modifyResult(final int messageID, final Result result) throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest.getRequest() instanceof ModifyRequest) {
                        ((ResultLdapPromiseImpl<ModifyRequest, Result>) pendingRequest).setResultOrError(result);
                        return;
                    }
                    throw newUnexpectedResponseException(messageID, result);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void searchResult(final int messageID, final Result result) throws DecodeException,
                IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.removePendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest.getRequest() instanceof SearchRequest) {
                        ((ResultLdapPromiseImpl<SearchRequest, Result>) pendingRequest).setResultOrError(result);
                    } else {
                        throw newUnexpectedResponseException(messageID, result);
                    }
                }
            }
        }

        @Override
        public void searchResultEntry(final int messageID, final SearchResultEntry entry)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.getPendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest instanceof SearchResultLdapPromiseImpl) {
                        ((SearchResultLdapPromiseImpl) pendingRequest).handleEntry(entry);
                    } else {
                        throw newUnexpectedResponseException(messageID, entry);
                    }
                }
            }
        }

        @Override
        public void searchResultReference(final int messageID, final SearchResultReference reference)
                throws DecodeException, IOException {
            final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(context.getConnection());
            if (ldapConnection != null) {
                final ResultLdapPromiseImpl<?, ?> pendingRequest = ldapConnection.getPendingRequest(messageID);
                if (pendingRequest != null) {
                    if (pendingRequest instanceof SearchResultLdapPromiseImpl) {
                        ((SearchResultLdapPromiseImpl) pendingRequest).handleReference(reference);
                    } else {
                        throw newUnexpectedResponseException(messageID, reference);
                    }
                }
            }
        }

        /** Needed in order to expose type information. */
        private <R extends ExtendedResult> void handleExtendedResult0(
                final GrizzlyLDAPConnection conn, final ExtendedResultLdapPromiseImpl<R> promise,
                final ExtendedResult result) throws DecodeException {
            final R decodedResponse = promise.decodeResult(result, conn.getLDAPOptions().get(LDAP_DECODE_OPTIONS));

            if (result.getResultCode() == ResultCode.SUCCESS
                    && promise.getRequest() instanceof StartTLSExtendedRequest) {
                try {
                    final StartTLSExtendedRequest request = (StartTLSExtendedRequest) promise.getRequest();
                    conn.startTLS(request.getSSLContext(), request.getEnabledProtocols(),
                            request.getEnabledCipherSuites(),
                            new EmptyCompletionHandler<SSLEngine>() {
                                @Override
                                public void completed(final SSLEngine result) {
                                    conn.setBindOrStartTLSInProgress(false);
                                    promise.setResultOrError(decodedResponse);
                                }

                                @Override
                                public void failed(final Throwable throwable) {
                                    final Result errorResult = newResult(CLIENT_SIDE_LOCAL_ERROR)
                                            .setCause(throwable).setDiagnosticMessage("SSL handshake failed");
                                    conn.setBindOrStartTLSInProgress(false);
                                    conn.close(null, false, errorResult);
                                    promise.adaptErrorResult(errorResult);
                                }
                            });
                    return;
                } catch (final IOException e) {
                    final Result errorResult = newResult(CLIENT_SIDE_LOCAL_ERROR).setCause(e)
                            .setDiagnosticMessage(e.getMessage());
                    promise.adaptErrorResult(errorResult);
                    conn.close(null, false, errorResult);
                    return;
                }
            }

            promise.setResultOrError(decodedResponse);
        }
    }

    /**
     * Creates a client filter with provided options and max size of ASN1
     * elements.
     *
     * @param options
     *            allow to control how request and responses are decoded
     * @param maxASN1ElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     */
    LDAPClientFilter(final DecodeOptions options, final int maxASN1ElementSize) {
        super(options, maxASN1ElementSize);
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx, final Throwable error) {
        final Connection<?> connection = ctx.getConnection();
        if (!connection.isOpen()) {
            // Grizzly doesn't not deregister the read interest from the
            // selector so closing the connection results in an EOFException.
            // Just ignore errors on closed connections.
            return;
        }
        final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(connection);

        Result errorResult;
        if (error instanceof EOFException) {
            // FIXME: Is this the best result code?
            errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_SERVER_DOWN).setCause(error);
        } else {
            // FIXME: what other sort of IOExceptions can be thrown?
            // FIXME: Is this the best result code?
            errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR).setCause(error);
        }
        ldapConnection.close(null, false, errorResult);
    }

    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        final Connection<?> connection = ctx.getConnection();
        final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.remove(connection);
        if (ldapConnection != null) {
            final Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
            ldapConnection.close(null, false, errorResult);
        }
        return ctx.getInvokeAction();
    }

    @Override
    final void handleReadException(FilterChainContext ctx, IOException e) {
        final GrizzlyLDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(ctx.getConnection());
        final Result errorResult =
                Responses.newResult(ResultCode.CLIENT_SIDE_DECODING_ERROR).setCause(e)
                        .setDiagnosticMessage(e.getMessage());
        ldapConnection.close(null, false, errorResult);
    }

    /**
     * Returns the response handler associated to the provided connection and
     * context.
     * <p>
     * If no handler exists yet for this context, a new one is created and
     * recorded for the connection.
     *
     * @param ctx
     *            current filter chain context
     * @return the response handler associated to the context, which can be a
     *         new one if no handler have been created yet
     */
    @Override
    final LDAPBaseHandler getLDAPHandler(final FilterChainContext ctx) {
        Connection<?> connection = ctx.getConnection();
        ClientResponseHandler handler = RESPONSE_HANDLER_ATTR.get(connection);
        if (handler == null) {
            LDAPReader<ASN1BufferReader> reader =
                    GrizzlyUtils.createReader(decodeOptions, maxASN1ElementSize, connection
                            .getTransport().getMemoryManager());
            handler = new ClientResponseHandler(reader);
            RESPONSE_HANDLER_ATTR.set(connection, handler);
        }
        handler.setFilterChainContext(ctx);
        return handler;
    }

    /**
     * Associate a LDAP connection to the provided Grizzly connection.
     *
     * @param connection
     *            Grizzly connection
     * @param ldapConnection
     *            LDAP connection
     */
    void registerConnection(final Connection<?> connection,
            final GrizzlyLDAPConnection ldapConnection) {
        LDAP_CONNECTION_ATTR.set(connection, ldapConnection);
    }
}
