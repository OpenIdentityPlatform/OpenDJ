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

package com.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.LDAPConstants.OID_NOTICE_OF_DISCONNECTION;

import java.io.EOFException;
import java.io.IOException;

import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Grizzly filter implementation for decoding LDAP responses and handling client
 * side logic for SSL and SASL operations over LDAP.
 */
final class LDAPClientFilter extends BaseFilter {
    private static final Attribute<LDAPConnection> LDAP_CONNECTION_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("LDAPClientConnection");
    private static final Attribute<ASN1BufferReader> LDAP_ASN1_READER_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("LDAPASN1Reader");

    private final int maxASN1ElementSize;
    private final LDAPReader ldapReader;

    private static final AbstractLDAPMessageHandler<FilterChainContext> CLIENT_RESPONSE_HANDLER =
            new AbstractLDAPMessageHandler<FilterChainContext>() {
                @Override
                public void addResult(final FilterChainContext ctx, final int messageID,
                        final Result result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPFutureResultImpl) {
                                final LDAPFutureResultImpl future =
                                        (LDAPFutureResultImpl) pendingRequest;
                                if (future.getRequest() instanceof AddRequest) {
                                    future.setResultOrError(result);
                                    return;
                                }
                            }
                            throw new UnexpectedResponseException(messageID, result);
                        }
                    }
                }

                @Override
                public void bindResult(final FilterChainContext ctx, final int messageID,
                        final BindResult result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPBindFutureResultImpl) {
                                final LDAPBindFutureResultImpl future =
                                        ((LDAPBindFutureResultImpl) pendingRequest);
                                final BindClient bindClient = future.getBindClient();

                                try {
                                    if (!bindClient.evaluateResult(result)) {
                                        // The server is expecting a multi stage
                                        // bind response.
                                        final int msgID =
                                                ldapConnection.continuePendingBindRequest(future);

                                        final ASN1BufferWriter asn1Writer =
                                                ASN1BufferWriter.getWriter();
                                        try {
                                            final GenericBindRequest nextRequest =
                                                    bindClient.nextBindRequest();
                                            new LDAPWriter().bindRequest(asn1Writer, msgID, 3,
                                                    nextRequest);
                                            ctx.write(asn1Writer.getBuffer(), null);
                                        } finally {
                                            asn1Writer.recycle();
                                        }
                                        return;
                                    }
                                } catch (final ErrorResultException e) {
                                    ldapConnection.setBindOrStartTLSInProgress(false);
                                    future.adaptErrorResult(e.getResult());
                                    return;
                                } catch (final IOException e) {
                                    // FIXME: I18N need to have a better error
                                    // message.
                                    // FIXME: Is this the best result code?
                                    ldapConnection.setBindOrStartTLSInProgress(false);
                                    final Result errorResult =
                                            Responses
                                                    .newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR)
                                                    .setDiagnosticMessage(
                                                            "An error occurred during multi-stage authentication")
                                                    .setCause(e);
                                    future.adaptErrorResult(errorResult);
                                    return;
                                }

                                if (result.getResultCode() == ResultCode.SUCCESS) {
                                    final ConnectionSecurityLayer l =
                                            bindClient.getConnectionSecurityLayer();
                                    if (l != null) {
                                        // The connection needs to be secured by
                                        // the SASL mechanism.
                                        ldapConnection
                                                .installFilter(new ConnectionSecurityLayerFilter(l,
                                                        ctx.getConnection().getTransport()
                                                                .getMemoryManager()));
                                    }
                                }

                                ldapConnection.setBindOrStartTLSInProgress(false);
                                future.setResultOrError(result);
                                return;
                            }
                            throw new UnexpectedResponseException(messageID, result);
                        }
                    }
                }

                @Override
                public void compareResult(final FilterChainContext ctx, final int messageID,
                        final CompareResult result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPCompareFutureResultImpl) {
                                final LDAPCompareFutureResultImpl future =
                                        (LDAPCompareFutureResultImpl) pendingRequest;
                                future.setResultOrError(result);
                                return;
                            }
                            throw new UnexpectedResponseException(messageID, result);
                        }
                    }
                }

                @Override
                public void deleteResult(final FilterChainContext ctx, final int messageID,
                        final Result result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPFutureResultImpl) {
                                final LDAPFutureResultImpl future =
                                        (LDAPFutureResultImpl) pendingRequest;
                                if (future.getRequest() instanceof DeleteRequest) {
                                    future.setResultOrError(result);
                                    return;
                                }
                            }
                            throw new UnexpectedResponseException(messageID, result);
                        }
                    }
                }

                @Override
                public void extendedResult(final FilterChainContext ctx, final int messageID,
                        final ExtendedResult result) throws UnexpectedResponseException,
                        IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        if (messageID == 0) {
                            // Unsolicited notification received.
                            if ((result.getOID() != null)
                                    && result.getOID().equals(OID_NOTICE_OF_DISCONNECTION)) {
                                // Treat this as a connection error.
                                final Result errorResult =
                                        Responses
                                                .newResult(result.getResultCode())
                                                .setDiagnosticMessage(result.getDiagnosticMessage());
                                ldapConnection.close(null, true, errorResult);
                            } else {
                                ldapConnection.handleUnsolicitedNotification(result);
                            }
                        } else {
                            final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                    ldapConnection.removePendingRequest(messageID);

                            if (pendingRequest != null) {
                                if (pendingRequest instanceof LDAPExtendedFutureResultImpl<?>) {
                                    final LDAPExtendedFutureResultImpl<?> extendedFuture =
                                            ((LDAPExtendedFutureResultImpl<?>) pendingRequest);
                                    try {
                                        handleExtendedResult0(ldapConnection, extendedFuture,
                                                result);
                                    } catch (final DecodeException de) {
                                        // FIXME: should the connection be closed as
                                        // well?
                                        final Result errorResult =
                                                Responses.newResult(
                                                        ResultCode.CLIENT_SIDE_DECODING_ERROR)
                                                        .setDiagnosticMessage(
                                                                de.getLocalizedMessage()).setCause(
                                                                de);
                                        extendedFuture.adaptErrorResult(errorResult);
                                    }
                                } else {
                                    throw new UnexpectedResponseException(messageID, result);
                                }
                            }
                        }
                    }
                }

                @Override
                public void intermediateResponse(final FilterChainContext ctx, final int messageID,
                        final IntermediateResponse response) throws UnexpectedResponseException,
                        IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.getPendingRequest(messageID);

                        if (pendingRequest != null) {
                            pendingRequest.handleIntermediateResponse(response);
                        }
                    }
                }

                @Override
                public void modifyDNResult(final FilterChainContext ctx, final int messageID,
                        final Result result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPFutureResultImpl) {
                                final LDAPFutureResultImpl future =
                                        (LDAPFutureResultImpl) pendingRequest;
                                if (future.getRequest() instanceof ModifyDNRequest) {
                                    future.setResultOrError(result);
                                    return;
                                }
                            }
                            throw new UnexpectedResponseException(messageID, result);
                        }
                    }
                }

                @Override
                public void modifyResult(final FilterChainContext ctx, final int messageID,
                        final Result result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPFutureResultImpl) {
                                final LDAPFutureResultImpl future =
                                        (LDAPFutureResultImpl) pendingRequest;
                                if (future.getRequest() instanceof ModifyRequest) {
                                    future.setResultOrError(result);
                                    return;
                                }
                            }
                            throw new UnexpectedResponseException(messageID, result);
                        }
                    }
                }

                @Override
                public void searchResult(final FilterChainContext ctx, final int messageID,
                        final Result result) throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.removePendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPSearchFutureResultImpl) {
                                ((LDAPSearchFutureResultImpl) pendingRequest)
                                        .setResultOrError(result);
                            } else {
                                throw new UnexpectedResponseException(messageID, result);
                            }
                        }
                    }
                }

                @Override
                public void searchResultEntry(final FilterChainContext ctx, final int messageID,
                        final SearchResultEntry entry) throws UnexpectedResponseException,
                        IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.getPendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPSearchFutureResultImpl) {
                                ((LDAPSearchFutureResultImpl) pendingRequest).handleEntry(entry);
                            } else {
                                throw new UnexpectedResponseException(messageID, entry);
                            }
                        }
                    }
                }

                @Override
                public void searchResultReference(final FilterChainContext ctx,
                        final int messageID, final SearchResultReference reference)
                        throws UnexpectedResponseException, IOException {
                    final LDAPConnection ldapConnection =
                            LDAP_CONNECTION_ATTR.get(ctx.getConnection());
                    if (ldapConnection != null) {
                        final AbstractLDAPFutureResultImpl<?> pendingRequest =
                                ldapConnection.getPendingRequest(messageID);

                        if (pendingRequest != null) {
                            if (pendingRequest instanceof LDAPSearchFutureResultImpl) {
                                ((LDAPSearchFutureResultImpl) pendingRequest)
                                        .handleReference(reference);
                            } else {
                                throw new UnexpectedResponseException(messageID, reference);
                            }
                        }
                    }
                }

                // Needed in order to expose type information.
                private <R extends ExtendedResult> void handleExtendedResult0(
                        final LDAPConnection conn, final LDAPExtendedFutureResultImpl<R> future,
                        final ExtendedResult result) throws DecodeException {
                    final R decodedResponse =
                            future.decodeResult(result, conn.getLDAPOptions().getDecodeOptions());

                    if (future.getRequest() instanceof StartTLSExtendedRequest) {
                        if (result.getResultCode() == ResultCode.SUCCESS) {
                            try {
                                final StartTLSExtendedRequest request =
                                        (StartTLSExtendedRequest) future.getRequest();
                                conn.startTLS(request.getSSLContext(), request
                                        .getEnabledProtocols(), request.getEnabledCipherSuites(),
                                        new EmptyCompletionHandler<SSLEngine>() {
                                            @Override
                                            public void completed(final SSLEngine result) {
                                                conn.setBindOrStartTLSInProgress(false);
                                                future.setResultOrError(decodedResponse);
                                            }

                                            @Override
                                            public void failed(final Throwable throwable) {
                                                final Result errorResult =
                                                        Responses.newResult(
                                                                ResultCode.CLIENT_SIDE_LOCAL_ERROR)
                                                                .setCause(throwable)
                                                                .setDiagnosticMessage(
                                                                        "SSL handshake failed");
                                                conn.setBindOrStartTLSInProgress(false);
                                                conn.close(null, false, errorResult);
                                                future.adaptErrorResult(errorResult);
                                            }
                                        });
                                return;
                            } catch (final IOException e) {
                                final Result errorResult =
                                        Responses.newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR)
                                                .setCause(e).setDiagnosticMessage(e.getMessage());
                                future.adaptErrorResult(errorResult);
                                conn.close(null, false, errorResult);
                                return;
                            }
                        }
                    }

                    future.setResultOrError(decodedResponse);
                }
            };

    LDAPClientFilter(final LDAPReader ldapReader, final int maxASN1ElementSize) {
        this.ldapReader = ldapReader;
        this.maxASN1ElementSize = maxASN1ElementSize;
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
        final LDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(connection);

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
        final LDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.remove(connection);
        if (ldapConnection != null) {
            final Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
            ldapConnection.close(null, false, errorResult);
        }
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer buffer = (Buffer) ctx.getMessage();
        ASN1BufferReader asn1Reader = LDAP_ASN1_READER_ATTR.get(ctx.getConnection());
        if (asn1Reader == null) {
            asn1Reader =
                    new ASN1BufferReader(maxASN1ElementSize, ctx.getConnection().getTransport()
                            .getMemoryManager());
            LDAP_ASN1_READER_ATTR.set(ctx.getConnection(), asn1Reader);
        }
        asn1Reader.appendBytesRead(buffer);

        try {
            while (asn1Reader.elementAvailable()) {
                ldapReader.decode(asn1Reader, CLIENT_RESPONSE_HANDLER, ctx);
            }
        } catch (IOException ioe) {
            final LDAPConnection ldapConnection = LDAP_CONNECTION_ATTR.get(ctx.getConnection());
            final Result errorResult =
                    Responses.newResult(ResultCode.CLIENT_SIDE_DECODING_ERROR).setCause(ioe)
                            .setDiagnosticMessage(ioe.getMessage());
            ldapConnection.close(null, false, errorResult);
            throw ioe;
        } finally {
            asn1Reader.disposeBytesRead();
        }

        return ctx.getStopAction();
    }

    void registerConnection(final Connection<?> connection, final LDAPConnection ldapConnection) {
        LDAP_CONNECTION_ATTR.set(connection, ldapConnection);
    }
}
