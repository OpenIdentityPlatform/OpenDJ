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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

import com.forgerock.opendj.ldap.controls.AccountUsabilityRequestControl;
import com.forgerock.opendj.ldap.controls.AccountUsabilityResponseControl;
import org.forgerock.util.Options;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.TestCaseUtils.*;
import static org.forgerock.opendj.ldap.LDAPListener.*;

/**
 * A simple ldap server that manages 1000 entries and used for running
 * testcases.
 * <p>
 * FIXME: make it MT-safe.
 */
@SuppressWarnings("javadoc")
public class LDAPServer implements ServerConnectionFactory<LDAPClientContext, Integer> {
    /** Creates an abandonable request from the ordinary requests. */
    private static class AbandonableRequest implements Request {
        /** The request. */
        private final Request request;

        /** Whether is has been cancelled. */
        private final AtomicBoolean isCanceled;

        /** Ctor. */
        AbandonableRequest(final Request request) {
            this.request = request;
            this.isCanceled = new AtomicBoolean(false);
        }

        @Override
        public Request addControl(final Control cntrl) {
            return request.addControl(cntrl);
        }

        @Override
        public boolean containsControl(final String oid) {
            return request.containsControl(oid);
        }

        @Override
        public <C extends Control> C getControl(final ControlDecoder<C> decoder,
                final DecodeOptions options) throws DecodeException {
            return request.getControl(decoder, options);
        }

        @Override
        public List<Control> getControls() {
            return request.getControls();
        }

        void cancel() {
            isCanceled.set(true);
        }

        boolean isCanceled() {
            return isCanceled.get();
        }
    }

    /** The singleton instance. */
    private static final LDAPServer INSTANCE = new LDAPServer();

    /**
     * Returns the singleton instance.
     *
     * @return Singleton instance.
     */
    public static LDAPServer getInstance() {
        return INSTANCE;
    }

    private class LDAPServerConnection implements ServerConnection<Integer> {

        private final LDAPClientContext clientContext;
        private SaslServer saslServer;

        private LDAPServerConnection(LDAPClientContext clientContext) {
            this.clientContext = clientContext;
        }

        @Override
        public void handleAbandon(final Integer context, final AbandonRequest request)
                throws UnsupportedOperationException {
            // Check if we have any concurrent operation with this message id.
            final AbandonableRequest req = requestsInProgress.get(request.getRequestID());
            if (req == null) {
                // Nothing to do here.
                return;
            }
            // Cancel the request
            req.cancel();
            // No response is needed.
        }

        @Override
        public void handleAdd(final Integer context, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> handler) throws UnsupportedOperationException {
            Result result = null;
            final AbandonableRequest abReq = new AbandonableRequest(request);
            requestsInProgress.put(context, abReq);
            // Get the DN.
            final DN dn = request.getName();
            if (entryMap.containsKey(dn)) {
                // duplicate entry.
                result = Responses.newResult(ResultCode.ENTRY_ALREADY_EXISTS);
                handler.handleException(newLdapException(result));
                // doesn't matter if it was canceled.
                requestsInProgress.remove(context);
                return;
            }

            // Create an entry out of this request.
            final SearchResultEntry entry = Responses.newSearchResultEntry(dn);
            for (final Control control : request.getControls()) {
                entry.addControl(control);
            }

            for (final Attribute attr : request.getAllAttributes()) {
                entry.addAttribute(attr);
            }

            if (abReq.isCanceled()) {
                result = Responses.newResult(ResultCode.CANCELLED);
                handler.handleException(newLdapException(result));
                requestsInProgress.remove(context);
                return;
            }
            // Add this to the map.
            entryMap.put(dn, entry);
            requestsInProgress.remove(context);
            result = Responses.newResult(ResultCode.SUCCESS);
            handler.handleResult(result);
        }

        @Override
        public void handleBind(final Integer context, final int version, final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<BindResult> resultHandler) throws UnsupportedOperationException {
            // TODO: all bind types.
            final AbandonableRequest abReq = new AbandonableRequest(request);
            requestsInProgress.put(context, abReq);
            if (request.getAuthenticationType() == LDAP.TYPE_AUTHENTICATION_SASL
                    && request instanceof GenericBindRequest) {
                ASN1Reader reader =
                        ASN1.getReader(((GenericBindRequest) request).getAuthenticationValue());
                try {
                    String saslMech = reader.readOctetStringAsString();
                    ByteString saslCred;
                    if (reader.hasNextElement()) {
                        saslCred = reader.readOctetString();
                    } else {
                        saslCred = ByteString.empty();
                    }

                    if (saslServer == null
                            || !saslServer.getMechanismName().equalsIgnoreCase(saslMech)) {
                        final Map<String, String> props = new HashMap<>();
                        props.put(Sasl.QOP, "auth-conf,auth-int,auth");
                        saslServer =
                                Sasl.createSaslServer(saslMech, "ldap",
                                        listener.getHostName(), props,
                                        new CallbackHandler() {
                                            @Override
                                            public void handle(Callback[] callbacks)
                                                    throws IOException,
                                                    UnsupportedCallbackException {
                                                for (final Callback callback : callbacks) {
                                                    if (callback instanceof NameCallback) {
                                                        // Do nothing
                                                    } else if (callback instanceof PasswordCallback) {
                                                        ((PasswordCallback) callback)
                                                                .setPassword("password"
                                                                        .toCharArray());
                                                    } else if (callback instanceof AuthorizeCallback) {
                                                        ((AuthorizeCallback) callback)
                                                                .setAuthorized(true);
                                                    } else if (callback instanceof RealmCallback) {
                                                        // Do nothing
                                                    } else {
                                                        throw new UnsupportedCallbackException(
                                                                callback);

                                                    }
                                                }
                                            }
                                        });
                    }

                    byte[] challenge = saslServer.evaluateResponse(saslCred.toByteArray());
                    if (saslServer.isComplete()) {
                        resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS)
                                .setServerSASLCredentials(ByteString.wrap(challenge)));

                        String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
                        if ("auth-int".equalsIgnoreCase(qop) || "auth-conf".equalsIgnoreCase(qop)) {
                            ConnectionSecurityLayer csl = new ConnectionSecurityLayer() {
                                @Override
                                public void dispose() {
                                    try {
                                        saslServer.dispose();
                                    } catch (SaslException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public byte[] unwrap(byte[] incoming, int offset, int len) throws LdapException {
                                    try {
                                        return saslServer.unwrap(incoming, offset, len);
                                    } catch (SaslException e) {
                                        throw newLdapException(
                                                Responses.newResult(ResultCode.OPERATIONS_ERROR).setCause(e));
                                    }
                                }

                                @Override
                                public byte[] wrap(byte[] outgoing, int offset, int len) throws LdapException {
                                    try {
                                        return saslServer.wrap(outgoing, offset, len);
                                    } catch (SaslException e) {
                                        throw newLdapException(
                                                Responses.newResult(ResultCode.OPERATIONS_ERROR).setCause(e));
                                    }
                                }
                            };

                            clientContext.enableConnectionSecurityLayer(csl);
                        }

                    } else {
                        resultHandler.handleResult(Responses.newBindResult(
                                ResultCode.SASL_BIND_IN_PROGRESS).setServerSASLCredentials(
                                ByteString.wrap(challenge)));
                    }
                } catch (Exception e) {
                    resultHandler.handleException(newLdapException(Responses
                            .newBindResult(ResultCode.OPERATIONS_ERROR).setCause(e)
                            .setDiagnosticMessage(e.toString())));
                }
            } else {
                resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
            }
            requestsInProgress.remove(context);
        }

        @Override
        public void handleConnectionClosed(final Integer context, final UnbindRequest request) {
            close();
        }

        private void close() {
            if (saslServer != null) {
                try {
                    saslServer.dispose();
                } catch (SaslException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void handleConnectionDisconnected(ResultCode resultCode, String message) {
            close();
        }

        @Override
        public void handleConnectionError(final Throwable error) {
            close();
        }

        @Override
        public void handleCompare(final Integer context, final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<CompareResult> resultHandler)
                throws UnsupportedOperationException {
            CompareResult result = null;
            final AbandonableRequest abReq = new AbandonableRequest(request);
            requestsInProgress.put(context, abReq);
            // Get the DN.
            final DN dn = request.getName();
            if (!entryMap.containsKey(dn)) {
                // entry not found.
                result = Responses.newCompareResult(ResultCode.NO_SUCH_ATTRIBUTE);
                resultHandler.handleException(newLdapException(result));
                // doesn't matter if it was canceled.
                requestsInProgress.remove(context);
                return;
            }

            // Get the entry.
            final Entry entry = entryMap.get(dn);
            final AttributeDescription attrDesc = request.getAttributeDescription();
            for (final Attribute attr : entry.getAllAttributes(attrDesc)) {
                final Iterator<ByteString> it = attr.iterator();
                while (it.hasNext()) {
                    final ByteString s = it.next();
                    if (abReq.isCanceled()) {
                        final Result r = Responses.newResult(ResultCode.CANCELLED);
                        resultHandler.handleException(newLdapException(r));
                        requestsInProgress.remove(context);
                        return;
                    }
                    if (s.equals(request.getAssertionValue())) {
                        result = Responses.newCompareResult(ResultCode.COMPARE_TRUE);
                        resultHandler.handleResult(result);
                        requestsInProgress.remove(context);
                        return;
                    }
                }
            }
            result = Responses.newCompareResult(ResultCode.COMPARE_FALSE);
            resultHandler.handleResult(result);
            requestsInProgress.remove(context);
        }

        @Override
        public void handleDelete(final Integer context, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> handler) throws UnsupportedOperationException {
            Result result = null;
            final AbandonableRequest abReq = new AbandonableRequest(request);
            requestsInProgress.put(context, abReq);
            // Get the DN.
            final DN dn = request.getName();
            if (!entryMap.containsKey(dn)) {
                // entry is not found.
                result = Responses.newResult(ResultCode.NO_SUCH_OBJECT);
                handler.handleException(newLdapException(result));
                // doesn't matter if it was canceled.
                requestsInProgress.remove(context);
                return;
            }

            if (abReq.isCanceled()) {
                result = Responses.newResult(ResultCode.CANCELLED);
                handler.handleException(newLdapException(result));
                requestsInProgress.remove(context);
                return;
            }
            // Remove this from the map.
            entryMap.remove(dn);
            requestsInProgress.remove(context);
            result = Responses.newResult(ResultCode.SUCCESS);
            handler.handleResult(result);
        }

        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final Integer context,
                final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<R> resultHandler) throws UnsupportedOperationException {
            if (request.getOID().equals(StartTLSExtendedRequest.OID)) {
                final R result = request.getResultDecoder().newExtendedErrorResult(ResultCode.SUCCESS, "", "");
                resultHandler.handleResult(result);
                clientContext.enableTLS(sslContext, null, sslContext.getSocketFactory()
                        .getSupportedCipherSuites(), false, false);
            }
        }

        @Override
        public void handleModify(final Integer context, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            // TODO:
        }

        @Override
        public void handleModifyDN(final Integer context, final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            // TODO
        }

        @Override
        public void handleSearch(final Integer context, final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler,
            final LdapResultHandler<Result> resultHandler) throws UnsupportedOperationException {
            Result result = null;
            final AbandonableRequest abReq = new AbandonableRequest(request);
            requestsInProgress.put(context, abReq);
            // Get the DN.
            final DN dn = request.getName();
            if (!entryMap.containsKey(dn)) {
                // Entry not found.
                result = Responses.newResult(ResultCode.NO_SUCH_OBJECT);
                resultHandler.handleException(newLdapException(result));
                // Should searchResultHandler handle anything?

                // doesn't matter if it was canceled.
                requestsInProgress.remove(context);
                return;
            }

            if (abReq.isCanceled()) {
                result = Responses.newResult(ResultCode.CANCELLED);
                resultHandler.handleException(newLdapException(result));
                requestsInProgress.remove(context);
                return;
            }

            final SearchResultEntry e = Responses.newSearchResultEntry(new LinkedHashMapEntry(entryMap.get(dn)));
            // Check we have had any controls in the request.
            for (final Control control : request.getControls()) {
                if (control.getOID().equals(AccountUsabilityRequestControl.OID)) {
                    e.addControl(AccountUsabilityResponseControl.newControl(false, false, false, 10, false, 0));
                }
            }
            entryHandler.handleEntry(e);
            result = Responses.newResult(ResultCode.SUCCESS);
            resultHandler.handleResult(result);
            requestsInProgress.remove(context);
        }
    }

    /** The mapping between entry DNs and the corresponding entries. */
    private final ConcurrentHashMap<DN, Entry> entryMap = new ConcurrentHashMap<>();
    /** The LDAP listener. */
    private LDAPListener listener;
    /** Whether the server is running. */
    private volatile boolean isRunning;

    /**
     * The mapping between the message id and the requests the server is
     * currently handling.
     */
    private final ConcurrentHashMap<Integer, AbandonableRequest> requestsInProgress = new ConcurrentHashMap<>();

    private SSLContext sslContext;

    private LDAPServer() {
        // Add the root dse first.
        entryMap.put(DN.rootDN(), Entries.unmodifiableEntry(new LinkedHashMapEntry()));
        for (int i = 0; i < 1000; i++) {
            final String dn = String.format("uid=user.%d,ou=people,o=test", i);
            final String cn = String.format("cn: user.%d", i);
            final String sn = String.format("sn: %d", i);
            final String uid = String.format("uid: user.%d", i);

            // See
            // org.forgerock.opendj.ldap.ConnectionFactoryTestCase.testSchemaUsage().
            final String mail = String.format("mail: user.%d@example.com", i);

            final DN d = DN.valueOf(dn);
            final Entry e =
                    new LinkedHashMapEntry("dn: " + dn, "objectclass: person",
                            "objectclass: inetorgperson", "objectclass: top", cn, sn, uid, mail);
            entryMap.put(d, Entries.unmodifiableEntry(e));
        }
    }

    @Override
    public ServerConnection<Integer> handleAccept(final LDAPClientContext context) {
        return new LDAPServerConnection(context);
    }

    /**
     * Returns whether the server is running or not.
     *
     * @return Whether the server is running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Starts the server.
     *
     * @exception Exception
     */
    public synchronized void start() throws Exception {
        if (isRunning) {
            return;
        }
        sslContext = new SSLContextBuilder().getSSLContext();
        listener = new LDAPListener(findFreeSocketAddress(), getInstance(),
                        Options.defaultOptions().set(CONNECT_MAX_BACKLOG, 4096));
        isRunning = true;
    }

    /**
     * Stops the server.
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        listener.close();
        isRunning = false;
    }

    /**
     * Returns the socket address of the server.
     *
     * @return The socket address of the server.
     */
    public synchronized InetSocketAddress getSocketAddress() {
        if (!isRunning) {
            throw new IllegalStateException("Server is not running");
        }
        return listener.getSocketAddress();
    }
}
