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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CancelExtendedRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.StartTLSExtendedRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.AttributeType;

/**
 * This example is based on the {@link Proxy}. This example does no load
 * balancing, but instead rewrites attribute descriptions and DN suffixes in
 * requests to and responses from a directory server using hard coded
 * configuration.
 * <ul>
 * <li>It transforms DNs ending in {@code o=example} on the client side to end
 * in {@code dc=example,dc=com} on the server side and vice versa.
 * <li>It transforms the attribute description {@code fullname} on the client
 * side to {@code cn} on the server side and vice versa.
 * </ul>
 *
 * This example has a number of restrictions.
 * <ul>
 * <li>It does not support SSL connections.
 * <li>It does not support StartTLS.
 * <li>It does not support Abandon or Cancel requests.
 * <li>It has very basic authentication and authorization support.
 * <li>It does not rewrite bind DNs.
 * <li>It uses proxied authorization, so if you use OpenDJ directory server, you
 * must set the {@code proxied-auth} privilege for the proxy user.
 * <li>It does not touch matched DNs in results.
 * <li>It does not rewrite attributes with options in search result entries.
 * <li>It does not touch search result references.
 * </ul>
 * This example takes the following command line parameters:
 *
 * <pre>
 *  &lt;localAddress> &lt;localPort> &lt;proxyDN> &lt;proxyPassword> &lt;serverAddress> &lt;serverPort>
 * </pre>
 *
 * If you have imported the users from <a
 * href="http://opendj.forgerock.org/Example.ldif">Example.ldif</a>, then you
 * can set {@code proxyUserDN} to {@code cn=My App,ou=Apps,dc=example,dc=com}
 * and {@code proxyUserPassword} to {@code password}.
 */
public final class RewriterProxy {
    private static final class ProxyBackend implements RequestHandler<RequestContext> {

        // This example hard codes the attribute...
        private final String clientAttributeTypeName = "fullname";
        private final String serverAttributeTypeName = "cn";
        private final AttributeDescription clientAttributeDescription =
                AttributeDescription.valueOf(clientAttributeTypeName);
        private final AttributeDescription serverAttributeDescription =
                AttributeDescription.valueOf(serverAttributeTypeName);

        // ...and DN rewriting configuration.
        private final CharSequence clientSuffix = "o=example";
        private final CharSequence serverSuffix = "dc=example,dc=com";

        private final ConnectionFactory factory;
        private final ConnectionFactory bindFactory;

        private ProxyBackend(final ConnectionFactory factory, final ConnectionFactory bindFactory) {
            this.factory = factory;
            this.bindFactory = bindFactory;
        }

        private abstract class AbstractRequestCompletionHandler
                <R extends Result, H extends ResultHandler<R>>
                implements ResultHandler<R> {
            final H resultHandler;
            final Connection connection;

            AbstractRequestCompletionHandler(final Connection connection, final H resultHandler) {
                this.connection = connection;
                this.resultHandler = resultHandler;
            }

            @Override
            public final void handleErrorResult(final ErrorResultException error) {
                connection.close();
                resultHandler.handleErrorResult(error);
            }

            @Override
            public final void handleResult(final R result) {
                connection.close();
                resultHandler.handleResult(result);
            }

        }

        private abstract class ConnectionCompletionHandler<R extends Result> implements
                ResultHandler<Connection> {
            private final ResultHandler<R> resultHandler;

            ConnectionCompletionHandler(final ResultHandler<R> resultHandler) {
                this.resultHandler = resultHandler;
            }

            @Override
            public final void handleErrorResult(final ErrorResultException error) {
                resultHandler.handleErrorResult(error);
            }

            @Override
            public abstract void handleResult(Connection connection);

        }

        private final class RequestCompletionHandler<R extends Result> extends
                AbstractRequestCompletionHandler<R, ResultHandler<R>> {
            RequestCompletionHandler(final Connection connection,
                    final ResultHandler<R> resultHandler) {
                super(connection, resultHandler);
            }
        }

        private final class SearchRequestCompletionHandler extends
                AbstractRequestCompletionHandler<Result, SearchResultHandler> implements
                SearchResultHandler {

            SearchRequestCompletionHandler(final Connection connection,
                    final SearchResultHandler resultHandler) {
                super(connection, resultHandler);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public final boolean handleEntry(SearchResultEntry entry) {
                return resultHandler.handleEntry(rewrite(entry));
            }

            private SearchResultEntry rewrite(SearchResultEntry entry) {

                // Replace server attributes with client attributes.
                Set<Attribute> attrsToAdd = new HashSet<Attribute>();
                Set<AttributeDescription> attrsToRemove = new HashSet<AttributeDescription>();

                for (Attribute a : entry.getAllAttributes(serverAttributeDescription)) {
                    AttributeDescription ad = a.getAttributeDescription();
                    AttributeType at = ad.getAttributeType();
                    if (at.equals(serverAttributeDescription.getAttributeType())) {
                        AttributeDescription clientAttrDesc =
                                AttributeDescription.valueOf(ad.toString()
                                        .replaceFirst(
                                                serverAttributeTypeName,
                                                clientAttributeTypeName));
                        attrsToAdd.add(Attributes.renameAttribute(a, clientAttrDesc));
                        attrsToRemove.add(ad);
                    }
                }

                if (!attrsToAdd.isEmpty() && !attrsToRemove.isEmpty()) {
                    for (Attribute a : attrsToAdd) {
                        entry.addAttribute(a);
                    }
                    for (AttributeDescription ad : attrsToRemove) {
                        entry.removeAttribute(ad);
                    }
                }

                // Transform the server DN suffix into a client DN suffix.
                return entry.setName(entry.getName().toString()
                        .replace(serverSuffix, clientSuffix));

            }

            /**
             * {@inheritDoc}
             */
            @Override
            public final boolean handleReference(final SearchResultReference reference) {
                return resultHandler.handleReference(reference);
            }

        }

        private volatile ProxiedAuthV2RequestControl proxiedAuthControl = null;

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleAdd(final RequestContext requestContext, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.addAsync(rewrite(request), intermediateResponseHandler, innerHandler);
                        }

                        private AddRequest rewrite(final AddRequest request) {

                            // Transform the client DN into a server DN.
                            AddRequest rewrittenRequest =
                                    Requests.copyOfAddRequest(request);
                            rewrittenRequest.setName(request.getName().toString()
                                    .replace(clientSuffix, serverSuffix));

                            // Transform the client attribute names into server
                            // attribute names, fullname;lang-fr ==> cn;lang-fr.
                            for (Attribute a
                                    : request.getAllAttributes(clientAttributeDescription)) {
                                if (a != null) {
                                    String ad = a
                                            .getAttributeDescriptionAsString()
                                            .replaceFirst(clientAttributeTypeName,
                                                          serverAttributeTypeName);
                                    Attribute serverAttr =
                                            Attributes.renameAttribute(a,
                                                    AttributeDescription.valueOf(ad));
                                    rewrittenRequest.addAttribute(serverAttr);
                                    rewrittenRequest.removeAttribute(
                                            a.getAttributeDescription());
                                }
                            }

                            return rewrittenRequest;
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleBind(final RequestContext requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<BindResult> resultHandler) {

            if (request.getAuthenticationType() != ((byte) 0x80)) {
                // TODO: SASL authentication not implemented.
                resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "non-SIMPLE authentication not supported: "
                                + request.getAuthenticationType()));
            } else {
                // Authenticate using a separate bind connection pool, because
                // we don't want to change the state of the pooled connection.
                final ConnectionCompletionHandler<BindResult> outerHandler =
                        new ConnectionCompletionHandler<BindResult>(resultHandler) {

                            @Override
                            public void handleResult(final Connection connection) {
                                final ResultHandler<BindResult> innerHandler =
                                        new ResultHandler<BindResult>() {

                                            @Override
                                            public final void handleErrorResult(
                                                    final ErrorResultException error) {
                                                connection.close();
                                                resultHandler.handleErrorResult(error);
                                            }

                                            @Override
                                            public final void handleResult(final BindResult result) {
                                                connection.close();
                                                proxiedAuthControl =
                                                        ProxiedAuthV2RequestControl
                                                                .newControl("dn:"
                                                                        + request.getName());
                                                resultHandler.handleResult(result);
                                            }
                                        };
                                connection.bindAsync(rewrite(request), intermediateResponseHandler,
                                        innerHandler);
                            }

                            private BindRequest rewrite(final BindRequest request) {
                                // TODO: Transform client DN into server DN.
                                return request;
                            }

                        };

                proxiedAuthControl = null;
                bindFactory.getConnectionAsync(outerHandler);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleCompare(final RequestContext requestContext,
                final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<CompareResult> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<CompareResult> outerHandler =
                    new ConnectionCompletionHandler<CompareResult>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<CompareResult> innerHandler =
                                    new RequestCompletionHandler<CompareResult>(connection,
                                            resultHandler);
                            connection.compareAsync(rewrite(request), intermediateResponseHandler,
                                    innerHandler);
                        }

                        private CompareRequest rewrite(CompareRequest request) {

                            // Transform the client attribute name into a server
                            // attribute name, fullname;lang-fr ==> cn;lang-fr.
                            String ad = request.getAttributeDescription().toString();
                            if (ad.toLowerCase().startsWith(
                                    clientAttributeTypeName.toLowerCase())) {
                                String serverAttrDesc = ad
                                        .replaceFirst(clientAttributeTypeName,
                                                      serverAttributeTypeName);
                                request.setAttributeDescription(
                                        AttributeDescription.valueOf(
                                                serverAttrDesc));
                            }

                            // Transform the client DN into a server DN.
                            return request.setName(request.getName().toString()
                                    .replace(clientSuffix, serverSuffix));
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.deleteAsync(rewrite(request), intermediateResponseHandler,
                                    innerHandler);
                        }

                        private DeleteRequest rewrite(DeleteRequest request) {
                            // Transform the client DN into a server DN.
                            return request.setName(request.getName().toString()
                                    .replace(clientSuffix, serverSuffix));
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(
                final RequestContext requestContext, final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<R> resultHandler) {
            if (request.getOID().equals(CancelExtendedRequest.OID)) {
                // TODO: not implemented.
                resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "Cancel extended request operation not supported"));
            } else if (request.getOID().equals(StartTLSExtendedRequest.OID)) {
                // TODO: not implemented.
                resultHandler.handleErrorResult(newErrorResult(ResultCode.PROTOCOL_ERROR,
                        "StartTLS extended request operation not supported"));
            } else {
                // Forward all other extended operations.
                addProxiedAuthControl(request);

                final ConnectionCompletionHandler<R> outerHandler =
                        new ConnectionCompletionHandler<R>(resultHandler) {

                            @Override
                            public void handleResult(final Connection connection) {
                                final RequestCompletionHandler<R> innerHandler =
                                        new RequestCompletionHandler<R>(connection, resultHandler);
                                connection.extendedRequestAsync(request,
                                        intermediateResponseHandler, innerHandler);
                            }

                            // TODO: Rewrite PasswordModifyExtendedRequest,
                            //       WhoAmIExtendedResult

                        };

                factory.getConnectionAsync(outerHandler);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModify(final RequestContext requestContext, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.modifyAsync(rewrite(request), intermediateResponseHandler,
                                    innerHandler);
                        }

                        private ModifyRequest rewrite(final ModifyRequest request) {

                            // Transform the client DN into a server DN.
                            ModifyRequest rewrittenRequest =
                                    Requests.newModifyRequest(request.getName().toString()
                                            .replace(clientSuffix, serverSuffix));

                            // Transform the client attribute names into server
                            // attribute names, fullname;lang-fr ==> cn;lang-fr.
                            List<Modification> mods = request.getModifications();
                            for (Modification mod : mods) {
                                Attribute a = mod.getAttribute();
                                AttributeDescription ad = a.getAttributeDescription();
                                AttributeType at = ad.getAttributeType();

                                if (at.equals(clientAttributeDescription.getAttributeType())) {
                                    AttributeDescription serverAttrDesc =
                                            AttributeDescription.valueOf(ad.toString()
                                                    .replaceFirst(
                                                            clientAttributeTypeName,
                                                            serverAttributeTypeName));
                                    rewrittenRequest.addModification(new Modification(
                                            mod.getModificationType(),
                                            Attributes.renameAttribute(a, serverAttrDesc)));
                                } else {
                                    rewrittenRequest.addModification(mod);
                                }
                            }
                            for (Control control : request.getControls()) {
                                rewrittenRequest.addControl(control);
                            }

                            return rewrittenRequest;
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleModifyDN(final RequestContext requestContext,
                final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final ResultHandler<Result> resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final RequestCompletionHandler<Result> innerHandler =
                                    new RequestCompletionHandler<Result>(connection, resultHandler);
                            connection.modifyDNAsync(rewrite(request), intermediateResponseHandler,
                                    innerHandler);
                        }

                        private ModifyDNRequest rewrite(ModifyDNRequest request) {
                            // Transform the client DNs into server DNs.
                            if (request.getNewSuperior() != null) {
                                return request
                                        .setName(request.getName().toString()
                                                .replace(clientSuffix, serverSuffix))
                                        .setNewSuperior(request.getNewSuperior().toString()
                                                .replace(clientSuffix, serverSuffix));
                            } else {
                                return request
                                        .setName(request.getName().toString()
                                                .replace(clientSuffix, serverSuffix));
                            }
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleSearch(final RequestContext requestContext, final SearchRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final SearchResultHandler resultHandler) {
            addProxiedAuthControl(request);
            final ConnectionCompletionHandler<Result> outerHandler =
                    new ConnectionCompletionHandler<Result>(resultHandler) {

                        @Override
                        public void handleResult(final Connection connection) {
                            final SearchRequestCompletionHandler innerHandler =
                                    new SearchRequestCompletionHandler(connection, resultHandler);
                            connection.searchAsync(rewrite(request), intermediateResponseHandler,
                                    innerHandler);
                        }

                        private SearchRequest rewrite(final SearchRequest request) {
                            // Transform the client attribute names to a server
                            // attribute names, fullname;lang-fr ==> cn;lang-fr.
                            String[] a = new String[request.getAttributes().size()];
                            int count = 0;
                            for (String attrName : request.getAttributes()) {
                                if (attrName.toLowerCase().startsWith(
                                        clientAttributeTypeName.toLowerCase())) {
                                    a[count] = attrName.replaceFirst(
                                            clientAttributeTypeName,
                                            serverAttributeTypeName);
                                } else {
                                    a[count] = attrName;
                                }
                                ++count;
                            }

                            // Rewrite the baseDN, and rewrite the Filter in
                            // dangerously lazy fashion. All the filter rewrite
                            // does is a string replace, so if the client
                            // attribute name appears in the value part of the
                            // AVA, this implementation will not work.
                            return Requests.newSearchRequest(
                                    DN.valueOf(request.getName().toString()
                                            .replace(clientSuffix, serverSuffix)),
                                    request.getScope(),
                                    Filter.valueOf(request.getFilter().toString()
                                            .replace(clientAttributeTypeName,
                                                     serverAttributeTypeName)),
                                    a);
                        }

                    };

            factory.getConnectionAsync(outerHandler);
        }

        private void addProxiedAuthControl(final Request request) {
            final ProxiedAuthV2RequestControl control = proxiedAuthControl;
            if (control != null) {
                request.addControl(control);
            }
        }

    }

    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: local address, local port, proxy
     *            user DN, proxy user password, server address, server port
     */
    public static void main(final String[] args) {
        if (args.length != 6) {
            System.err.println("Usage:"
                    + "\tlocalAddress localPort proxyDN proxyPassword "
                    + "serverAddress serverPort");
            System.exit(1);
        }

        final String localAddress = args[0];
        final int localPort = Integer.parseInt(args[1]);
        final String proxyDN = args[2];
        final String proxyPassword = args[3];
        final String remoteAddress = args[4];
        final int remotePort = Integer.parseInt(args[5]);

        // Create connection factories.
        final ConnectionFactory factory =
                Connections.newCachedConnectionPool(
                        Connections.newAuthenticatedConnectionFactory(
                                new LDAPConnectionFactory(remoteAddress, remotePort),
                                Requests.newSimpleBindRequest(
                                        proxyDN, proxyPassword.toCharArray())));
        final ConnectionFactory bindFactory =
                Connections.newCachedConnectionPool(new LDAPConnectionFactory(
                        remoteAddress, remotePort));

        // Create a server connection adapter.
        final ProxyBackend backend = new ProxyBackend(factory, bindFactory);
        final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                Connections.newServerConnectionFactory(backend);

        // Create listener.
        final LDAPListenerOptions options = new LDAPListenerOptions().setBacklog(4096);
        LDAPListener listener = null;
        try {
            listener = new LDAPListener(localAddress, localPort, connectionHandler, options);
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } catch (final IOException e) {
            System.out.println("Error listening on " + localAddress + ":" + localPort);
            e.printStackTrace();
        } finally {
            if (listener != null) {
                listener.close();
            }
        }
    }

    private RewriterProxy() {
        // Not used.
    }
}
