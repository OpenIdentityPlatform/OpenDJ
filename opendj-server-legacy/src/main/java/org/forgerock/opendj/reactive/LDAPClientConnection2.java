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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2010-2016 ForgeRock AS.
 */
package org.forgerock.opendj.reactive;

import static com.forgerock.reactive.RxJavaStreams.streamFromPublisher;
import static org.forgerock.opendj.io.LDAP.*;
import static org.forgerock.util.Utils.closeSilently;
import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.logDisconnect;
import static org.opends.server.util.ServerConstants.OID_START_TLS_REQUEST;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.sasl.SaslServer;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPClientContextEventListener;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.util.Reject;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.AbandonOperationBasis;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.core.CompareOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperationBasis;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.QueueingStrategy;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.SearchOperationBasis;
import org.opends.server.core.UnbindOperationBasis;
import org.opends.server.extensions.TLSCapableConnection;
import org.opends.server.protocols.ldap.AbandonRequestProtocolOp;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPReader;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.LDAPStatistics;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.util.TimeThread;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.forgerock.reactive.Consumer;
import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Stream;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;

/**
 * This class defines an LDAP client connection, which is a type of client connection that will be accepted by an
 * instance of the LDAP connection handler and have its requests decoded by an LDAP request handler.
 */
public final class LDAPClientConnection2 extends ClientConnection implements TLSCapableConnection,
        ReactiveHandler<QueueingStrategy, LdapRequestEnvelope, Stream<Response>> {
    private static final String REACTIVE_OUT = "reactive.out";

    /** The tracer object for the debug logger. */
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The time that the last operation was completed. */
    private final AtomicLong lastCompletionTime;
    /** The next operation ID that should be used for this connection. */
    private final AtomicLong nextOperationID;

    /**
     * Indicates whether the Directory Server believes this connection to be valid and available for communication.
     */
    private volatile boolean connectionValid;

    /**
     * Indicates whether this connection is about to be closed. This will be used to prevent accepting new requests
     * while a disconnect is in progress.
     */
    private boolean disconnectRequested;

    /**
     * Indicates whether the connection should keep statistics regarding the operations that it is performing.
     */
    private final boolean keepStats;

    /** The set of all operations currently in progress on this connection. */
    private final ConcurrentHashMap<Integer, Operation> operationsInProgress;

    /**
     * The number of operations performed on this connection. Used to compare with the resource limits of the network
     * group.
     */
    private final AtomicLong operationsPerformed;

    /** The port on the client from which this connection originated. */
    private final int clientPort;
    /** The LDAP version that the client is using to communicate with the server. */
    private int ldapVersion;
    /** The port on the server to which this client has connected. */
    private final int serverPort;

    /** The reference to the connection handler that accepted this connection. */
    private final LDAPConnectionHandler2 connectionHandler;
    /** The statistics tracker associated with this client connection. */
    private final LDAPStatistics statTracker;
    private final boolean useNanoTime;

    /** The connection ID assigned to this connection. */
    private final long connectionID;

    /** The lock used to provide threadsafe access to the set of operations in progress. */
    private final Object opsInProgressLock;

    /** The socket channel with which this client connection is associated. */
    private final LDAPClientContext clientContext;

    /** The string representation of the address of the client. */
    private final String clientAddress;
    /** The name of the protocol that the client is using to communicate with the server. */
    private final String protocol;
    /** The string representation of the address of the server to which the client has connected. */
    private final String serverAddress;

    /**
     * Creates a new LDAP client connection with the provided information.
     *
     * @param connectionHandler
     *            The connection handler that accepted this connection.
     * @param clientContext
     *            The socket channel that may be used to communicate with the client.
     * @param protocol
     *            String representing the protocol (LDAP or LDAP+SSL).
     */
    LDAPClientConnection2(LDAPConnectionHandler2 connectionHandler, LDAPClientContext clientContext, String protocol,
            boolean keepStats) {
        this.connectionHandler = connectionHandler;
        this.clientContext = clientContext;
        opsInProgressLock = new Object();
        ldapVersion = 3;
        lastCompletionTime = new AtomicLong(TimeThread.getTime());
        nextOperationID = new AtomicLong(0);
        connectionValid = true;
        disconnectRequested = false;
        operationsInProgress = new ConcurrentHashMap<>();
        operationsPerformed = new AtomicLong(0);
        this.keepStats = keepStats;
        this.protocol = protocol;

        clientAddress = clientContext.getPeerAddress().getAddress().getHostAddress();
        clientPort = clientContext.getPeerAddress().getPort();
        serverAddress = clientContext.getLocalAddress().getAddress().getHostAddress();
        serverPort = clientContext.getLocalAddress().getPort();

        statTracker = this.connectionHandler.getStatTracker();
        if (keepStats) {
            statTracker.updateConnect();
            this.useNanoTime = DirectoryServer.getCoreConfigManager().isUseNanoTime();
        } else {
            this.useNanoTime = false;
        }

        connectionID = DirectoryServer.newConnectionAccepted(this);
        clientContext.addListener(new LDAPClientContextEventListener() {
            @Override
            public void handleConnectionError(LDAPClientContext context, Throwable error) {
                if (error instanceof LocalizableException) {
                    disconnect(
                            DisconnectReason.PROTOCOL_ERROR, true, ((LocalizableException) error).getMessageObject());
                } else {
                    disconnect(DisconnectReason.PROTOCOL_ERROR, true, null);
                }
            }

            @Override
            public void handleConnectionDisconnected(LDAPClientContext context, ResultCode resultCode,
                    String diagnosticMessage) {
                disconnect(DisconnectReason.SERVER_ERROR, false, null);
            }

            @Override
            public void handleConnectionClosed(LDAPClientContext context, UnbindRequest unbindRequest) {
                disconnect(DisconnectReason.CLIENT_DISCONNECT, false, null);
            }
        });
    }

    /**
     * Retrieves the connection ID assigned to this connection.
     *
     * @return The connection ID assigned to this connection.
     */
    @Override
    public long getConnectionID() {
        return connectionID;
    }

    /**
     * Retrieves the connection handler that accepted this client connection.
     *
     * @return The connection handler that accepted this client connection.
     */
    @Override
    public ConnectionHandler<?> getConnectionHandler() {
        return connectionHandler;
    }

    /**
     * Retrieves the socket channel that can be used to communicate with the client.
     *
     * @return The socket channel that can be used to communicate with the client.
     */
    @Override
    public SocketChannel getSocketChannel() {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the protocol that the client is using to communicate with the Directory Server.
     *
     * @return The protocol that the client is using to communicate with the Directory Server.
     */
    @Override
    public String getProtocol() {
        return protocol;
    }

    /**
     * Retrieves a string representation of the address of the client.
     *
     * @return A string representation of the address of the client.
     */
    @Override
    public String getClientAddress() {
        return clientAddress;
    }

    /**
     * Retrieves the port number for this connection on the client system.
     *
     * @return The port number for this connection on the client system.
     */
    @Override
    public int getClientPort() {
        return clientPort;
    }

    /**
     * Retrieves a string representation of the address on the server to which the client connected.
     *
     * @return A string representation of the address on the server to which the client connected.
     */
    @Override
    public String getServerAddress() {
        return serverAddress;
    }

    /**
     * Retrieves the port number for this connection on the server system.
     *
     * @return The port number for this connection on the server system.
     */
    @Override
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Retrieves the <CODE>java.net.InetAddress</CODE> associated with the remote client system.
     *
     * @return The <CODE>java.net.InetAddress</CODE> associated with the remote client system. It may be
     *         <CODE>null</CODE> if the client is not connected over an IP-based connection.
     */
    @Override
    public InetAddress getRemoteAddress() {
        return clientContext.getPeerAddress().getAddress();
    }

    /**
     * Retrieves the <CODE>java.net.InetAddress</CODE> for the Directory Server system to which the client has
     * established the connection.
     *
     * @return The <CODE>java.net.InetAddress</CODE> for the Directory Server system to which the client has established
     *         the connection. It may be <CODE>null</CODE> if the client is not connected over an IP-based connection.
     */
    @Override
    public InetAddress getLocalAddress() {
        return clientContext.getLocalAddress().getAddress();
    }

    @Override
    public boolean isConnectionValid() {
        return this.connectionValid;
    }

    /**
     * Indicates whether this client connection is currently using a secure mechanism to communicate with the server.
     * Note that this may change over time based on operations performed by the client or server (e.g., it may go from
     * <CODE>false</CODE> to <CODE>true</CODE> if the client uses the StartTLS extended operation).
     *
     * @return <CODE>true</CODE> if the client connection is currently using a secure mechanism to communicate with the
     *         server, or <CODE>false</CODE> if not.
     */
    @Override
    public boolean isSecure() {
        return clientContext.getSSLSession() != null || clientContext.getSASLServer() != null;
    }

    /**
     * Sends a response to the client based on the information in the provided operation.
     *
     * @param operation
     *            The operation for which to send the response.
     */
    @Override
    public void sendResponse(Operation operation) {
        // Since this is the final response for this operation, we can go
        // ahead and remove it from the "operations in progress" list. It
        // can't be canceled after this point, and this will avoid potential
        // race conditions in which the client immediately sends another
        // request with the same message ID as was used for this operation.

        if (keepStats) {
            long time;
            if (useNanoTime) {
                time = operation.getProcessingNanoTime();
            } else {
                time = operation.getProcessingTime();
            }
            this.statTracker.updateOperationMonitoringData(operation.getOperationType(), time);
        }

        // Avoid sending the response if one has already been sent. This may happen
        // if operation processing encounters a run-time exception after sending the
        // response: the worker thread exception handling code will attempt to send
        // an error result to the client indicating that a problem occurred.
        if (removeOperationInProgress(operation.getMessageID())) {
            final Response response = operationToResponse(operation);
            final FlowableEmitter<Response> out = getAttachedEmitter(operation);
            if (response != null) {
                out.onNext(response);
            }
            out.onComplete();
        }
    }

    /**
     * Retrieves an LDAPMessage containing a response generated from the provided operation.
     *
     * @param operation
     *            The operation to use to generate the response LDAPMessage.
     * @return An LDAPMessage containing a response generated from the provided operation.
     */
    private Response operationToResponse(Operation operation) {
        ResultCode resultCode = operation.getResultCode();
        if (resultCode == null) {
            // This must mean that the operation has either not yet completed
            // or that it completed without a result for some reason. In any
            // case, log a message and set the response to "operations error".
            logger.error(ERR_LDAP_CLIENT_SEND_RESPONSE_NO_RESULT_CODE, operation.getOperationType(),
                    operation.getConnectionID(), operation.getOperationID());
            resultCode = DirectoryServer.getCoreConfigManager().getServerErrorResultCode();
        }

        LocalizableMessageBuilder errorMessage = operation.getErrorMessage();
        String matchedDN = operation.getMatchedDN() != null ? operation.getMatchedDN().toString() : null;

        // Referrals are not allowed for LDAPv2 clients.
        List<String> referralURLs;
        if (ldapVersion == 2) {
            referralURLs = null;

            if (resultCode == ResultCode.REFERRAL) {
                resultCode = ResultCode.CONSTRAINT_VIOLATION;
                errorMessage.append(ERR_LDAPV2_REFERRAL_RESULT_CHANGED.get());
            }

            List<String> opReferrals = operation.getReferralURLs();
            if (opReferrals != null && !opReferrals.isEmpty()) {
                StringBuilder referralsStr = new StringBuilder();
                Iterator<String> iterator = opReferrals.iterator();
                referralsStr.append(iterator.next());

                while (iterator.hasNext()) {
                    referralsStr.append(", ");
                    referralsStr.append(iterator.next());
                }

                errorMessage.append(ERR_LDAPV2_REFERRALS_OMITTED.get(referralsStr));
            }
        } else {
            referralURLs = operation.getReferralURLs();
        }

        final Result result;
        switch (operation.getOperationType()) {
        case ADD:
            result = Responses.newResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN);
            break;
        case BIND:
            result = Responses.newBindResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN)
                    .setServerSASLCredentials(((BindOperationBasis) operation).getServerSASLCredentials());
            break;
        case COMPARE:
            result = Responses.newCompareResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN);
            break;
        case DELETE:
            result = Responses.newResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN);
            break;
        case EXTENDED:
            // If this an LDAPv2 client, then we can't send this.
            if (ldapVersion == 2) {
                logger.error(ERR_LDAPV2_SKIPPING_EXTENDED_RESPONSE, getConnectionID(), operation.getOperationID(),
                        operation);
                return null;
            }

            ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
            result = Responses.newGenericExtendedResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN).setOID(extOp.getResponseOID()).setValue(extOp.getResponseValue());
            break;
        case MODIFY:
            result = Responses.newResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN);
            break;
        case MODIFY_DN:
            result = Responses.newResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN);
            break;
        case SEARCH:
            result = Responses.newResult(resultCode).setDiagnosticMessage(errorMessage.toString())
                    .setMatchedDN(matchedDN);
            break;
        default:
            // This must be a type of operation that doesn't have a response.
            // This shouldn't happen, so log a message and return.
            logger.error(ERR_LDAP_CLIENT_SEND_RESPONSE_INVALID_OP, operation.getOperationType(), getConnectionID(),
                    operation.getOperationID(), operation);
            return null;
        }
        if (referralURLs != null) {
            result.getReferralURIs().addAll(referralURLs);
        }

        // Controls are not allowed for LDAPv2 clients.
        if (ldapVersion != 2 && operation.getResponseControls() != null) {
            for (Control control : operation.getResponseControls()) {
                result.addControl(Converters.from(control));
            }
        }

        return result;
    }

    /**
     * Sends the provided search result entry to the client.
     *
     * @param searchOperation
     *            The search operation with which the entry is associated
     * @param searchEntry
     *            The search result entry to be sent to the client
     */
    @Override
    public void sendSearchEntry(final SearchOperation searchOperation, final SearchResultEntry searchEntry) {
        getAttachedEmitter(searchOperation).onNext(toResponse(searchEntry));
    }

    private FlowableEmitter<Response> getAttachedEmitter(final Operation operation) {
        return (FlowableEmitter<Response>) operation.getAttachment(REACTIVE_OUT);
    }

    private Response toResponse(final SearchResultEntry searchEntry) {
        return Responses.newSearchResultEntry(Converters.partiallyWrap(searchEntry, ldapVersion));
    }

    /**
     * Sends the provided search result reference to the client.
     *
     * @param searchOperation
     *            The search operation with which the reference is associated.
     * @param searchReference
     *            The search result reference to be sent to the client.
     * @return <CODE>true</CODE> if the client is able to accept referrals, or <CODE>false</CODE> if the client cannot
     *         handle referrals and no more attempts should be made to send them for the associated search operation.
     */
    @Override
    public boolean sendSearchReference(SearchOperation searchOperation, SearchResultReference searchReference) {
        // Make sure this is not an LDAPv2 client. If it is, then they can't
        // see referrals so we'll not send anything. Also, throw an
        // exception so that the core server will know not to try sending
        // any more referrals to this client for the rest of the operation.
        if (ldapVersion == 2) {
            logger.error(ERR_LDAPV2_SKIPPING_SEARCH_REFERENCE, getConnectionID(), searchOperation.getOperationID(),
                    searchReference);
            return false;
        }

        final FlowableEmitter<Response> out = getAttachedEmitter(searchOperation);
        out.onNext(Converters.from(searchReference));

        return true;
    }

    /**
     * Sends the provided intermediate response message to the client.
     *
     * @param intermediateResponse
     *            The intermediate response message to be sent.
     * @return <CODE>true</CODE> if processing on the associated operation should continue, or <CODE>false</CODE> if
     *         not.
     */
    @Override
    protected boolean sendIntermediateResponseMessage(IntermediateResponse intermediateResponse) {
        final Operation operation = intermediateResponse.getOperation();
        final FlowableEmitter<Response> emitter = getAttachedEmitter(operation);

        final Response response = Responses.newGenericIntermediateResponse(intermediateResponse.getOID(),
                intermediateResponse.getValue());
        for (Control control : intermediateResponse.getControls()) {
            response.addControl(Converters.from(control));
        }

        emitter.onNext(response);

        // The only reason we shouldn't continue processing is if the
        // connection is closed.
        return connectionValid;
    }

    /**
     * Closes the connection to the client, optionally sending it a message indicating the reason for the closure. Note
     * that the ability to send a notice of disconnection may not be available for all protocols or under all
     * circumstances.
     *
     * @param disconnectReason
     *            The disconnect reason that provides the generic cause for the disconnect.
     * @param sendNotification
     *            Indicates whether to try to provide notification to the client that the connection will be closed.
     * @param message
     *            The message to include in the disconnect notification response. It may be <CODE>null</CODE> if no
     *            message is to be sent.
     */
    @Override
    public void disconnect(DisconnectReason disconnectReason, boolean sendNotification, LocalizableMessage message) {
        // Set a flag indicating that the connection is being terminated so
        // that no new requests will be accepted. Also cancel all operations
        // in progress.
        synchronized (opsInProgressLock) {
            // If we are already in the middle of a disconnect, then don't
            // do anything.
            if (disconnectRequested) {
                return;
            }

            disconnectRequested = true;
        }

        if (keepStats) {
            statTracker.updateDisconnect();
        }

        if (connectionID >= 0) {
            DirectoryServer.connectionClosed(this);
        }

        // Indicate that this connection is no longer valid.
        connectionValid = false;

        final LocalizableMessage cancelMessage;
        if (message != null) {
            cancelMessage = new LocalizableMessageBuilder().append(disconnectReason.getClosureMessage()).append(": ")
                    .append(message).toMessage();
        } else {
            cancelMessage = disconnectReason.getClosureMessage();
        }
        cancelAllOperations(new CancelRequest(true, cancelMessage));
        finalizeConnectionInternal();

        // See if we should send a notification to the client. If so, then
        // construct and send a notice of disconnection unsolicited
        // response. Note that we cannot send this notification to an LDAPv2 client.
        if (sendNotification && ldapVersion != 2) {
            try {
                LocalizableMessage errMsg = message != null ? message
                        : INFO_LDAP_CLIENT_GENERIC_NOTICE_OF_DISCONNECTION.get();
                clientContext.disconnect(ResultCode.valueOf(toResultCode(disconnectReason)), errMsg.toString());
            } catch (Exception e) {
                // NYI -- Log a message indicating that we couldn't send the
                // notice of disconnection.
                logger.traceException(e);
            }
        } else {
            clientContext.disconnect();
        }

        // NYI -- Deregister the client connection from any server components that
        // might know about it.

        logDisconnect(this, disconnectReason, message);

        try {
            PluginConfigManager pluginManager = DirectoryServer.getPluginConfigManager();
            pluginManager.invokePostDisconnectPlugins(this, disconnectReason, message);
        } catch (Exception e) {
            logger.traceException(e);
        }
    }

    private int toResultCode(DisconnectReason disconnectReason) {
        switch (disconnectReason) {
        case PROTOCOL_ERROR:
            return LDAPResultCode.PROTOCOL_ERROR;
        case SERVER_SHUTDOWN:
            return LDAPResultCode.UNAVAILABLE;
        case SERVER_ERROR:
            return DirectoryServer.getCoreConfigManager().getServerErrorResultCode().intValue();
        case ADMIN_LIMIT_EXCEEDED:
        case IDLE_TIME_LIMIT_EXCEEDED:
        case MAX_REQUEST_SIZE_EXCEEDED:
        case IO_TIMEOUT:
            return LDAPResultCode.ADMIN_LIMIT_EXCEEDED;
        case CONNECTION_REJECTED:
            return LDAPResultCode.CONSTRAINT_VIOLATION;
        case INVALID_CREDENTIALS:
            return LDAPResultCode.INVALID_CREDENTIALS;
        default:
            return LDAPResultCode.OTHER;
        }
    }

    /**
     * Retrieves the set of operations in progress for this client connection. This list must not be altered by any
     * caller.
     *
     * @return The set of operations in progress for this client connection.
     */
    @Override
    public Collection<Operation> getOperationsInProgress() {
        return operationsInProgress.values();
    }

    /**
     * Retrieves the operation in progress with the specified message ID.
     *
     * @param messageID
     *            The message ID for the operation to retrieve.
     * @return The operation in progress with the specified message ID, or <CODE>null</CODE> if no such operation could
     *         be found.
     */
    @Override
    public Operation getOperationInProgress(int messageID) {
        return operationsInProgress.get(messageID);
    }

    /**
     * Adds the provided operation to the set of operations in progress for this client connection.
     *
     * @param operation
     *            The operation to add to the set of operations in progress for this client connection.
     * @throws DirectoryException
     *             If the operation is not added for some reason (e.g., the client already has reached the maximum
     *             allowed concurrent requests).
     */
    private void addOperationInProgress(final QueueingStrategy queueingStrategy, Operation operation)
            throws DirectoryException {
        int messageID = operation.getMessageID();

        // We need to grab a lock to ensure that no one else can add
        // operations to the queue while we are performing some preliminary
        // checks.
        try {
            synchronized (opsInProgressLock) {
                // If we're already in the process of disconnecting the client,
                // then reject the operation.
                if (disconnectRequested) {
                    LocalizableMessage message = WARN_CLIENT_DISCONNECT_IN_PROGRESS.get();
                    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
                }

                // Add the operation to the list of operations in progress for
                // this connection.
                Operation op = operationsInProgress.putIfAbsent(messageID, operation);

                // See if there is already an operation in progress with the
                // same message ID. If so, then we can't allow it.
                if (op != null) {
                    LocalizableMessage message = WARN_LDAP_CLIENT_DUPLICATE_MESSAGE_ID.get(messageID);
                    throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
                }
            }

            // Try to add the operation to the work queue,
            // or run it synchronously (typically for the administration connector)
            queueingStrategy.enqueueRequest(operation);
        } catch (DirectoryException de) {
            logger.traceException(de);

            operationsInProgress.remove(messageID);
            lastCompletionTime.set(TimeThread.getTime());

            throw de;
        } catch (Exception e) {
            logger.traceException(e);

            LocalizableMessage message = WARN_LDAP_CLIENT_CANNOT_ENQUEUE.get(getExceptionMessage(e));
            throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
        }
    }

    /**
     * Removes the provided operation from the set of operations in progress for this client connection. Note that this
     * does not make any attempt to cancel any processing that may already be in progress for the operation.
     *
     * @param messageID
     *            The message ID of the operation to remove from the set of operations in progress.
     * @return <CODE>true</CODE> if the operation was found and removed from the set of operations in progress, or
     *         <CODE>false</CODE> if not.
     */
    @Override
    public boolean removeOperationInProgress(int messageID) {
        Operation operation = operationsInProgress.remove(messageID);
        if (operation == null) {
            return false;
        }

        if (operation.getOperationType() == OperationType.ABANDON && keepStats
                && operation.getResultCode() == ResultCode.CANCELLED) {
            statTracker.updateAbandonedOperation();
        }

        lastCompletionTime.set(TimeThread.getTime());
        return true;
    }

    /**
     * Attempts to cancel the specified operation.
     *
     * @param messageID
     *            The message ID of the operation to cancel.
     * @param cancelRequest
     *            An object providing additional information about how the cancel should be processed.
     * @return A cancel result that either indicates that the cancel was successful or provides a reason that it was
     *         not.
     */
    @Override
    public CancelResult cancelOperation(int messageID, CancelRequest cancelRequest) {
        Operation op = operationsInProgress.get(messageID);
        if (op != null) {
            return op.cancel(cancelRequest);
        }

        // See if the operation is in the list of persistent searches.
        for (PersistentSearch ps : getPersistentSearches()) {
            if (ps.getMessageID() == messageID) {
                // We only need to find the first persistent search
                // associated with the provided message ID. The persistent search
                // will ensure that all other related persistent searches are cancelled.
                return ps.cancel();
            }
        }
        return new CancelResult(ResultCode.NO_SUCH_OPERATION, null);
    }

    /**
     * Attempts to cancel all operations in progress on this connection.
     *
     * @param cancelRequest
     *            An object providing additional information about how the cancel should be processed.
     */
    @Override
    public void cancelAllOperations(CancelRequest cancelRequest) {
        // Make sure that no one can add any new operations.
        synchronized (opsInProgressLock) {
            try {
                for (Operation o : operationsInProgress.values()) {
                    try {
                        o.abort(cancelRequest);

                        // TODO: Assume its cancelled?
                        if (keepStats) {
                            statTracker.updateAbandonedOperation();
                        }
                    } catch (Exception e) {
                        logger.traceException(e);
                    }
                }

                if (!operationsInProgress.isEmpty() || !getPersistentSearches().isEmpty()) {
                    lastCompletionTime.set(TimeThread.getTime());
                }

                operationsInProgress.clear();

                for (PersistentSearch persistentSearch : getPersistentSearches()) {
                    persistentSearch.cancel();
                }
            } catch (Exception e) {
                logger.traceException(e);
            }
        }
    }

    /**
     * Attempts to cancel all operations in progress on this connection except the operation with the specified message
     * ID.
     *
     * @param cancelRequest
     *            An object providing additional information about how the cancel should be processed.
     * @param messageID
     *            The message ID of the operation that should not be canceled.
     */
    @Override
    public void cancelAllOperationsExcept(CancelRequest cancelRequest, int messageID) {
        // Make sure that no one can add any new operations.
        synchronized (opsInProgressLock) {
            try {
                for (Map.Entry<Integer, Operation> entry : operationsInProgress.entrySet()) {
                    int msgID = entry.getKey();
                    if (msgID == messageID) {
                        continue;
                    }

                    Operation o = entry.getValue();
                    if (o != null) {
                        try {
                            o.abort(cancelRequest);

                            // TODO: Assume its cancelled?
                            if (keepStats) {
                                statTracker.updateAbandonedOperation();
                            }
                        } catch (Exception e) {
                            logger.traceException(e);
                        }
                    }

                    operationsInProgress.remove(msgID);
                    lastCompletionTime.set(TimeThread.getTime());
                }

                for (PersistentSearch persistentSearch : getPersistentSearches()) {
                    if (persistentSearch.getMessageID() == messageID) {
                        continue;
                    }

                    persistentSearch.cancel();
                    lastCompletionTime.set(TimeThread.getTime());
                }
            } catch (Exception e) {
                logger.traceException(e);
            }
        }
    }

    @Override
    public Selector getWriteSelector() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getMaxBlockedWriteTimeLimit() {
        return connectionHandler.getMaxBlockedWriteTimeLimit();
    }

    /**
     * Returns the total number of operations initiated on this connection.
     *
     * @return the total number of operations on this connection
     */
    @Override
    public long getNumberOfOperations() {
        return operationsPerformed.get();
    }

    /**
     * Processes the provided LDAP message read from the client and takes whatever action is appropriate. For most
     * requests, this will include placing the operation in the work queue. Certain requests (in particular, abandons
     * and unbinds) will be processed directly.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message to process.
     * @return <CODE>true</CODE> if the appropriate action was taken for the request, or <CODE>false</CODE> if there was
     *         a fatal error and the client has been disconnected as a result, or if the client unbound from the server.
     */
    @Override
    public Stream<Response> handle(final QueueingStrategy queueingStrategy, final LdapRequestEnvelope message) {
        return streamFromPublisher(
                new BlockingBackpressureSubscription(connectionHandler.getMaxBlockedWriteTimeLimit(),
                        Flowable.create(new FlowableOnSubscribe<Response>() {
                            @Override
                            public void subscribe(FlowableEmitter<Response> emitter) throws Exception {
                                try {
                                    processLDAPMessage(
                                            queueingStrategy, LDAPReader.readMessage(message.getContent()), emitter);
                                } finally {
                                    // We don't need the ASN1Reader anymore.
                                    closeSilently(message.getContent());
                                }
                            }
                        }, BackpressureStrategy.ERROR)))
                        .onNext(new Consumer<Response>() {
                            @Override
                            public void accept(final Response response) throws Exception {
                                if (keepStats) {
                                    statTracker.updateMessageWritten(
                                            toLdapResponseType(message, response), message.getMessageId());
                                }
                            }
                        });
    }

    private final byte toLdapResultType(final byte requestType) {
        switch (requestType) {
        case OP_TYPE_ADD_REQUEST:
            return OP_TYPE_ADD_RESPONSE;
        case OP_TYPE_BIND_REQUEST:
            return OP_TYPE_BIND_RESPONSE;
        case OP_TYPE_COMPARE_REQUEST:
            return OP_TYPE_COMPARE_RESPONSE;
        case OP_TYPE_DELETE_REQUEST:
            return OP_TYPE_DELETE_RESPONSE;
        case OP_TYPE_EXTENDED_REQUEST:
            return OP_TYPE_EXTENDED_RESPONSE;
        case OP_TYPE_MODIFY_DN_REQUEST:
            return OP_TYPE_MODIFY_DN_RESPONSE;
        case OP_TYPE_MODIFY_REQUEST:
            return OP_TYPE_MODIFY_RESPONSE;
        case OP_TYPE_SEARCH_REQUEST:
            return OP_TYPE_SEARCH_RESULT_DONE;
        default:
            throw new IllegalArgumentException("Unknown request: " + requestType);
        }
    }

    private final byte toLdapResponseType(final LdapRequestEnvelope rawRequest, final Response response) {
        if (response instanceof Result) {
            return toLdapResultType(rawRequest.getMessageType());
        }
        if (response instanceof org.forgerock.opendj.ldap.responses.IntermediateResponse) {
            return OP_TYPE_INTERMEDIATE_RESPONSE;
        }
        if (response instanceof org.forgerock.opendj.ldap.responses.SearchResultEntry) {
            return OP_TYPE_SEARCH_RESULT_ENTRY;
        }
        if (response instanceof org.forgerock.opendj.ldap.responses.SearchResultReference) {
            return OP_TYPE_SEARCH_RESULT_REFERENCE;
        }
        throw new IllegalArgumentException();
    }

    private boolean processLDAPMessage(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final FlowableEmitter<Response> out) {
        if (keepStats) {
            statTracker.updateMessageRead(message);
        }
        operationsPerformed.getAndIncrement();

        List<Control> opControls = message.getControls();

        // FIXME -- See if there is a bind in progress. If so, then deny
        // most kinds of operations.

        // Figure out what type of operation we're dealing with based on the
        // LDAP message. Abandon and unbind requests will be processed here.
        // All other types of requests will be encapsulated into operations
        // and append into the work queue to be picked up by a worker
        // thread. Any other kinds of LDAP messages (e.g., response
        // messages) are illegal and will result in the connection being
        // terminated.
        try {
            if (bindInProgress.get()) {
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, ERR_ENQUEUE_BIND_IN_PROGRESS.get());
            } else if (startTLSInProgress.get()) {
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, ERR_ENQUEUE_STARTTLS_IN_PROGRESS.get());
            } else if (saslBindInProgress.get() && message.getProtocolOpType() != OP_TYPE_BIND_REQUEST) {
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, ERR_ENQUEUE_SASLBIND_IN_PROGRESS.get());
            }

            boolean result;
            switch (message.getProtocolOpType()) {
            case OP_TYPE_ABANDON_REQUEST:
                return processAbandonRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_ADD_REQUEST:
                return processAddRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_BIND_REQUEST:
                boolean isSaslBind =
                    message.getBindRequestProtocolOp().getAuthenticationType() == AuthenticationType.SASL;
                bindInProgress.set(true);
                if (isSaslBind) {
                    saslBindInProgress.set(true);
                }
                result = processBindRequest(queueingStrategy, message, opControls, out);
                if (!result) {
                    bindInProgress.set(false);
                    if (isSaslBind) {
                        saslBindInProgress.set(false);
                    }
                }
                return result;
            case OP_TYPE_COMPARE_REQUEST:
                return processCompareRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_DELETE_REQUEST:
                return processDeleteRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_EXTENDED_REQUEST:
                boolean isStartTlsRequest = OID_START_TLS_REQUEST.equals(message.getExtendedRequestProtocolOp()
                        .getOID());
                if (isStartTlsRequest) {
                    startTLSInProgress.set(true);
                }
                result = processExtendedRequest(queueingStrategy, message, opControls, out);
                if (!result && isStartTlsRequest) {
                    startTLSInProgress.set(false);
                }
                return result;
            case OP_TYPE_MODIFY_REQUEST:
                return processModifyRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_MODIFY_DN_REQUEST:
                return processModifyDNRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_SEARCH_REQUEST:
                return processSearchRequest(queueingStrategy, message, opControls, out);
            case OP_TYPE_UNBIND_REQUEST:
                return processUnbindRequest(message, opControls);
            default:
                LocalizableMessage msg = ERR_LDAP_DISCONNECT_DUE_TO_INVALID_REQUEST_TYPE.get(
                        message.getProtocolOpName(), message.getMessageID());
                disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
                return false;
            }
        } catch (Exception e) {
            logger.traceException(e);

            LocalizableMessage msg = ERR_LDAP_DISCONNECT_DUE_TO_PROCESSING_FAILURE.get(message.getProtocolOpName(),
                    message.getMessageID(), e);
            disconnect(DisconnectReason.SERVER_ERROR, true, msg);
            return false;
        }
    }

    /**
     * Processes the provided LDAP message as an abandon request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the abandon request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processAbandonRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapVersion == 2 && !controls.isEmpty()) {
            disconnectControlsNotAllowed();
            return false;
        }

        // Create the abandon operation and add it into the work queue.
        AbandonRequestProtocolOp protocolOp = message.getAbandonRequestProtocolOp();
        AbandonOperationBasis abandonOp = new AbandonOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getIDToAbandon());
        abandonOp.setAttachment(REACTIVE_OUT, out);

        try {
            addOperationInProgress(queueingStrategy, abandonOp);
        } catch (DirectoryException de) {
            logger.traceException(de);

            // Don't send an error response since abandon operations
            // don't have a response.
        }

        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as an add request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the add request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processAddRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapV2HasControls(controls, out)) {
            return false;
        }

        // Create the add operation and add it into the work queue.
        AddRequestProtocolOp protocolOp = message.getAddRequestProtocolOp();
        AddOperationBasis addOp = new AddOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getDN(), protocolOp.getAttributes());

        addOperationToWorkQueue(queueingStrategy, out, addOp);
        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as a bind request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the bind request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processBindRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        BindRequestProtocolOp protocolOp = message.getBindRequestProtocolOp();

        // See if this is an LDAPv2 bind request, and if so whether that
        // should be allowed.
        String versionString;
        switch (ldapVersion = protocolOp.getProtocolVersion()) {
        case 2:
            versionString = "2";

            if (!connectionHandler.allowLDAPv2()) {
                out.onNext(Responses.newBindResult(ResultCode.PROTOCOL_ERROR)
                                    .setDiagnosticMessage(ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get().toString()));
                out.onComplete();
                disconnect(DisconnectReason.PROTOCOL_ERROR, false, ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
                return false;
            }

            if (!controls.isEmpty()) {
                // LDAPv2 clients aren't allowed to send controls.
                out.onNext(Responses.newBindResult(ResultCode.PROTOCOL_ERROR)
                                    .setDiagnosticMessage(ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get().toString()));
                out.onComplete();
                disconnectControlsNotAllowed();
                return false;
            }

            break;
        case 3:
            versionString = "3";
            break;
        default:
            // Unsupported protocol version. RFC4511 states that we MUST send
            // a protocol error back to the client.
            out.onNext(Responses.newBindResult(ResultCode.PROTOCOL_ERROR).setDiagnosticMessage(
                    ERR_LDAP_UNSUPPORTED_PROTOCOL_VERSION.get(ldapVersion).toString()));
            out.onComplete();
            disconnect(DisconnectReason.PROTOCOL_ERROR, false, ERR_LDAP_UNSUPPORTED_PROTOCOL_VERSION.get(ldapVersion));
            return false;
        }

        ByteString bindDN = protocolOp.getDN();

        BindOperationBasis bindOp;
        switch (protocolOp.getAuthenticationType()) {
        case SIMPLE:
            bindOp = new BindOperationBasis(this, nextOperationID.getAndIncrement(), message.getMessageID(), controls,
                    versionString, bindDN, protocolOp.getSimplePassword());
            break;
        case SASL:
            bindOp = new BindOperationBasis(this, nextOperationID.getAndIncrement(), message.getMessageID(), controls,
                    versionString, bindDN, protocolOp.getSASLMechanism(), protocolOp.getSASLCredentials());
            break;
        default:
            // This is an invalid authentication type, and therefore a
            // protocol error. As per RFC 2251, a protocol error in a bind
            // request must result in terminating the connection.
            LocalizableMessage msg = ERR_LDAP_INVALID_BIND_AUTH_TYPE.get(message.getMessageID(),
                    protocolOp.getAuthenticationType());
            disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
            return false;
        }

        // Add the operation into the work queue.
        bindOp.setAttachment(REACTIVE_OUT, out);
        try {
            addOperationInProgress(queueingStrategy, bindOp);
        } catch (DirectoryException de) {
            logger.traceException(de);

            final Result result = Responses.newBindResult(de.getResultCode());
            setDetails(result, de, bindOp.getResponseControls());
            out.onNext(result);
            out.onComplete();

            // If it was a protocol error, then terminate the connection.
            if (de.getResultCode() == ResultCode.PROTOCOL_ERROR) {
                LocalizableMessage msg = ERR_LDAP_DISCONNECT_DUE_TO_BIND_PROTOCOL_ERROR.get(message.getMessageID(),
                        de.getMessageObject());
                disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
            }
        }

        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as a compare request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the compare request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processCompareRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapVersion == 2 && !controls.isEmpty()) {
            // LDAPv2 clients aren't allowed to send controls.
            out.onNext(Responses.newCompareResult(ResultCode.PROTOCOL_ERROR)
                                .setDiagnosticMessage(ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get().toString()));
            out.onComplete();
            disconnectControlsNotAllowed();
            return false;
        }

        CompareRequestProtocolOp protocolOp = message.getCompareRequestProtocolOp();
        CompareOperationBasis compareOp = new CompareOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getDN(), protocolOp.getAttributeType(),
                protocolOp.getAssertionValue());

        // Add the operation into the work queue.
        compareOp.setAttachment(REACTIVE_OUT, out);
        try {
            addOperationInProgress(queueingStrategy, compareOp);
        } catch (DirectoryException de) {
            logger.traceException(de);

            final CompareResult result = Responses.newCompareResult(de.getResultCode());
            setDetails(result, de, compareOp.getResponseControls());
            out.onNext(result);
            out.onComplete();
        }

        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as a delete request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the delete request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processDeleteRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapV2HasControls(controls, out)) {
            return false;
        }

        DeleteRequestProtocolOp protocolOp = message.getDeleteRequestProtocolOp();
        DeleteOperationBasis deleteOp = new DeleteOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getDN());

        addOperationToWorkQueue(queueingStrategy, out, deleteOp);
        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as an extended request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the extended request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processExtendedRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        // See if this is an LDAPv2 client. If it is, then they should not
        // be issuing extended requests. We can't send a response that we
        // can be sure they can understand, so we have no choice but to
        // close the connection.
        if (ldapVersion == 2) {
            // LDAPv2 clients aren't allowed to send controls.
            LocalizableMessage msg = ERR_LDAPV2_EXTENDED_REQUEST_NOT_ALLOWED.get(getConnectionID(),
                    message.getMessageID());

            out.onNext(Responses.newResult(ResultCode.PROTOCOL_ERROR).setDiagnosticMessage(msg.toString()));
            out.onComplete();

            logger.error(msg);
            disconnect(DisconnectReason.PROTOCOL_ERROR, false, msg);
            return false;
        }

        // FIXME -- Do we need to handle certain types of request here?
        // -- StartTLS requests
        // -- Cancel requests

        ExtendedRequestProtocolOp protocolOp = message.getExtendedRequestProtocolOp();
        ExtendedOperationBasis extendedOp = new ExtendedOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getOID(), protocolOp.getValue());

        // Add the operation into the work queue.
        extendedOp.setAttachment(REACTIVE_OUT, out);
        try {
            addOperationInProgress(queueingStrategy, extendedOp);
        } catch (DirectoryException de) {
            logger.traceException(de);
            final Result result = Responses.newGenericExtendedResult(de.getResultCode());
            setDetails(result, de, extendedOp.getResponseControls());
            out.onNext(result);
            out.onComplete();
        }

        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as a modify request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the modify request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processModifyRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapV2HasControls(controls, out)) {
            return false;
        }

        ModifyRequestProtocolOp protocolOp = message.getModifyRequestProtocolOp();
        ModifyOperationBasis modifyOp = new ModifyOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getDN(), protocolOp.getModifications());

        addOperationToWorkQueue(queueingStrategy, out, modifyOp);
        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as a modify DN request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the modify DN request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processModifyDNRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapV2HasControls(controls, out)) {
            return false;
        }

        ModifyDNRequestProtocolOp protocolOp = message.getModifyDNRequestProtocolOp();
        ModifyDNOperationBasis modifyDNOp = new ModifyDNOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getEntryDN(), protocolOp.getNewRDN(),
                protocolOp.deleteOldRDN(), protocolOp.getNewSuperior());

        addOperationToWorkQueue(queueingStrategy, out, modifyDNOp);
        return connectionValid;
    }

    /**
     * Processes the provided LDAP message as a search request.
     *
     * @param queueingStrategy
     *            The {@link QueueingStrategy} to use for operation
     * @param message
     *            The LDAP message containing the search request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processSearchRequest(final QueueingStrategy queueingStrategy, final LDAPMessage message,
            final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapV2HasControls(controls, out)) {
            return false;
        }

        SearchRequestProtocolOp protocolOp = message.getSearchRequestProtocolOp();
        SearchOperationBasis searchOp = new SearchOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls, protocolOp.getBaseDN(), protocolOp.getScope(),
                protocolOp.getDereferencePolicy(), protocolOp.getSizeLimit(), protocolOp.getTimeLimit(),
                protocolOp.getTypesOnly(), protocolOp.getFilter(), protocolOp.getAttributes());

        addOperationToWorkQueue(queueingStrategy, out, searchOp);
        return connectionValid;
    }

    private void addOperationToWorkQueue(
            QueueingStrategy queueingStrategy, FlowableEmitter<Response> out, Operation operation) {
        operation.setAttachment(REACTIVE_OUT, out);
        try {
            addOperationInProgress(queueingStrategy, operation);
        } catch (DirectoryException de) {
            logger.traceException(de);

            final Result result = Responses.newResult(de.getResultCode());
            setDetails(result, de, operation.getResponseControls());
            out.onNext(result);
            out.onComplete();
        }
    }

    private void setDetails(Result result, DirectoryException de, List<Control> responseControls) {
        if (de.getLocalizedMessage() != null) {
            result.setDiagnosticMessage(de.getLocalizedMessage());
        }
        if (de.getMatchedDN() != null) {
            result.setMatchedDN(de.getMatchedDN().toString());
        }
        if (de.getReferralURLs() != null) {
            result.getReferralURIs().addAll(de.getReferralURLs());
        }
        if (ldapVersion != 2 && responseControls != null) {
            for (Control control : responseControls) {
                result.addControl(Converters.from(control));
            }
        }
    }

    /** LDAPv2 clients aren't allowed to send controls. */
    private boolean ldapV2HasControls(final List<Control> controls, final FlowableEmitter<Response> out) {
        if (ldapVersion == 2 && !controls.isEmpty()) {
            out.onNext(Responses.newResult(ResultCode.PROTOCOL_ERROR)
                                .setDiagnosticMessage(ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get().toString()));
            out.onComplete();
            disconnectControlsNotAllowed();
            return true;
        }
        return false;
    }

    private void disconnectControlsNotAllowed() {
        disconnect(DisconnectReason.PROTOCOL_ERROR, false, ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
    }

    /**
     * Processes the provided LDAP message as an unbind request.
     *
     * @param message
     *            The LDAP message containing the unbind request to process.
     * @param controls
     *            The set of pre-decoded request controls contained in the message.
     * @return <CODE>true</CODE> if the request was processed successfully, or <CODE>false</CODE> if not and the
     *         connection has been closed as a result (it is the responsibility of this method to close the connection).
     */
    private boolean processUnbindRequest(final LDAPMessage message, final List<Control> controls) {
        UnbindOperationBasis unbindOp = new UnbindOperationBasis(this, nextOperationID.getAndIncrement(),
                message.getMessageID(), controls);

        unbindOp.run();

        // The client connection will never be valid after an unbind.
        return false;
    }

    @Override
    public String getMonitorSummary() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("connID=\"");
        buffer.append(connectionID);
        buffer.append("\" connectTime=\"");
        buffer.append(getConnectTimeString());
        buffer.append("\" source=\"");
        buffer.append(clientAddress);
        buffer.append(":");
        buffer.append(clientPort);
        buffer.append("\" destination=\"");
        buffer.append(serverAddress);
        buffer.append(":");
        buffer.append(connectionHandler.getListeners().iterator().next().getPort());
        buffer.append("\" ldapVersion=\"");
        buffer.append(ldapVersion);
        buffer.append("\" authDN=\"");

        DN authDN = getAuthenticationInfo().getAuthenticationDN();
        if (authDN != null) {
            buffer.append(authDN);
        }

        buffer.append("\" security=\"");
        buffer.append("none");

        buffer.append("\" opsInProgress=\"");
        buffer.append(operationsInProgress.size());
        buffer.append("\"");

        int countPSearch = getPersistentSearches().size();
        if (countPSearch > 0) {
            buffer.append(" persistentSearches=\"");
            buffer.append(countPSearch);
            buffer.append("\"");
        }
        return buffer.toString();
    }

    /**
     * Appends a string representation of this client connection to the provided buffer.
     *
     * @param buffer
     *            The buffer to which the information should be appended.
     */
    @Override
    public void toString(StringBuilder buffer) {
        buffer.append("LDAP client connection from ");
        buffer.append(clientAddress);
        buffer.append(":");
        buffer.append(clientPort);
        buffer.append(" to ");
        buffer.append(serverAddress);
        buffer.append(":");
        buffer.append(serverPort);
    }

    @Override
    public boolean prepareTLS(final LocalizableMessageBuilder unavailableReason) {
        // Make sure that the connection handler allows the use of the
        // StartTLS operation.
        if (!connectionHandler.allowStartTLS()) {
            unavailableReason.append(ERR_LDAP_TLS_STARTTLS_NOT_ALLOWED.get());
            return false;
        }
        try {
            if (!clientContext.enableTLS(connectionHandler.createSSLEngine(), true)) {
                unavailableReason.append(ERR_LDAP_TLS_EXISTING_SECURITY_PROVIDER.get(SSLEngine.class.getName()));
                return false;
            }
        } catch (DirectoryException de) {
            logger.traceException(de);
            unavailableReason.append(ERR_LDAP_TLS_CANNOT_CREATE_TLS_PROVIDER.get(stackTraceToSingleLineString(de)));
            return false;
        }
        return true;
    }

    /**
     * Installs the SASL security layer on the underlying connection.
     *
     * @param saslServer
     *            The {@code SaslServer} which should be used to secure the conneciton.
     */
    public void enableSASL(final SaslServer saslServer) {
        clientContext.enableSASL(saslServer);
    }

    /**
     * Retrieves the length of time in milliseconds that this client connection has been idle. <BR>
     * <BR>
     * Note that the default implementation will always return zero. Subclasses associated with connection handlers
     * should override this method if they wish to provided idle time limit functionality.
     *
     * @return The length of time in milliseconds that this client connection has been idle.
     */
    @Override
    public long getIdleTime() {
        if (operationsInProgress.isEmpty() && getPersistentSearches().isEmpty()) {
            return TimeThread.getTime() - lastCompletionTime.get();
        } else {
            // There's at least one operation in progress, so it's not idle.
            return 0L;
        }
    }

    /**
     * Return the certificate chain array associated with a connection.
     *
     * @return The array of certificates associated with a connection.
     */
    public Certificate[] getClientCertificateChain() {
        final SSLSession sslSession = clientContext.getSSLSession();
        if (sslSession != null) {
            try {
                return sslSession.getPeerCertificates();
            } catch (SSLPeerUnverifiedException e) {
                logger.traceException(e);
            }
        }
        return new Certificate[0];
    }

    @Override
    public int getSSF() {
        return clientContext.getSecurityStrengthFactor();
    }

    /** Upstream -> BlockingBackpressureSubscription -> Downstream. */
    private final class BlockingBackpressureSubscription implements Subscription, Processor<Response, Response> {
        private final AtomicLong pendingRequests = new AtomicLong();
        private final AtomicInteger missedDrain = new AtomicInteger();
        private final BlockingQueue<Response> queue = new LinkedBlockingQueue<>(32);
        private final Publisher<Response> upstream;
        private final long writeTimeoutMillis;
        private Subscription subscription;
        private Subscriber<? super Response> downstream;
        private volatile boolean done;
        private Throwable error;
        private volatile boolean cancelled;

        BlockingBackpressureSubscription(final long maxBlockedWriteTimeLimit, final Publisher<Response> upstream) {
            this.upstream = upstream;
            this.writeTimeoutMillis = maxBlockedWriteTimeLimit == 0
                    ? 30000 // Do not wait indefinitely,
                    : maxBlockedWriteTimeLimit;
        }

        @Override
        public void subscribe(final Subscriber<? super Response> subscriber) {
            Reject.ifNull(subscriber);
            if (downstream != null) {
                // This publisher only support one subscriber.
                return;
            }
            downstream = subscriber;
            subscriber.onSubscribe(/* Subscription */ this);
            upstream.subscribe(/* Subscriber */ this);
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (subscription != null) {
                s.cancel();
                return;
            }
            subscription = s;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void request(final long n) {
            if (n == Long.MAX_VALUE) {
                pendingRequests.set(Long.MAX_VALUE);
            } else {
                // There is a known and accepted problem here regarding reactive-stream contract in the sense that
                // we're not supporting pendingRequests overflow (pendingRequests + n > Long.MAX_VALUE) for performance
                // reason since this should never happen in the context we're using it.
                pendingRequests.addAndGet(n);
            }
            drain();
        }

        // Taken from
        // https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#backpressure-and-cancellation
        private void drain() {
            if (missedDrain.getAndIncrement() != 0) {
                // Another thread is already executing this drain method.
                return;
            }

            int missed = 1;

            for (;;) {
                final long immutablePendingRequests = pendingRequests.get();
                long emitted = 0L;
                while (emitted != immutablePendingRequests) {
                    // Check if we should early exit because of cancellation
                    if (cancelled) {
                        return;
                    }

                    final Response response = queue.poll();
                    if (response != null) {
                        downstream.onNext(response);
                        emitted++;
                    } else if (done) {
                        // queue is empty and we received a completion (onError/onComplete) notification from upstream
                        forwardDoneEvent();
                        return;
                    } else {
                        // Queue is empty but upstream is not done yet.
                        break;
                    }
                }

                // Check if an onError/onComplete from upstream arrived.
                if (emitted == immutablePendingRequests) {
                    if (cancelled) {
                        return;
                    }

                    if (done && queue.isEmpty()) {
                        forwardDoneEvent();
                        return;
                    }
                }

                if (emitted != 0) {
                    pendingRequests.addAndGet(-emitted);
                }

                // Check to see if another thread asked for drain
                missed = missedDrain.addAndGet(-missed);
                if (missed == 0) {
                    // Nop, we can exit.
                    break;
                }
            }
        }

        private void forwardDoneEvent() {
            final Throwable immutableError = error;
            if (immutableError != null) {
                downstream.onError(immutableError);
            } else {
                downstream.onComplete();
            }
        }

        @Override
        public void onNext(final Response response) {
            try {
                if (queue.offer(response, writeTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    drain();
                } else {
                    // If we've gotten here, then the write timed out.
                    onError(new ClosedChannelException().fillInStackTrace());
                    return;
                }
            } catch (InterruptedException e) {
                onError(e);
            }
        }

        @Override
        public void onError(final Throwable error) {
            this.error = error;
            done = true;
            drain();
        }

        @Override
        public void onComplete() {
            done = true;
            drain();
        }

        @Override
        public void cancel() {
            cancelled = true;
            subscription.cancel();
        }
    }
}
