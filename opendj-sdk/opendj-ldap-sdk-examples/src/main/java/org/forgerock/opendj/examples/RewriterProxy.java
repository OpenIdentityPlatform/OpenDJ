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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.examples;

import static org.forgerock.opendj.ldap.Connections.newCachedConnectionPool;
import static org.forgerock.opendj.ldap.LDAPListener.CONNECT_MAX_BACKLOG;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.RequestHandlerFactory;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.util.Options;

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
 *  {@code <localAddress> <localPort> <proxyDN> <proxyPassword> <serverAddress> <serverPort>}
 * </pre>
 *
 * If you have imported the users from <a
 * href="http://opendj.forgerock.org/Example.ldif">Example.ldif</a>, then you
 * can set {@code proxyUserDN} to {@code cn=My App,ou=Apps,dc=example,dc=com}
 * and {@code proxyUserPassword} to {@code password}.
 */
public final class RewriterProxy {
    private static final class Rewriter implements RequestHandler<RequestContext> {

        /** This example hard codes the attribute... */
        private static final String CLIENT_ATTRIBUTE = "fullname";
        private static final String SERVER_ATTRIBUTE = "cn";

        /** ...and DN rewriting configuration. */
        private static final String CLIENT_SUFFIX = "o=example";
        private static final String SERVER_SUFFIX = "dc=example,dc=com";

        private final AttributeDescription clientAttributeDescription = AttributeDescription
                .valueOf(CLIENT_ATTRIBUTE);
        private final AttributeDescription serverAttributeDescription = AttributeDescription
                .valueOf(SERVER_ATTRIBUTE);

        /** Next request handler in the chain. */
        private final RequestHandler<RequestContext> nextHandler;

        private Rewriter(final RequestHandler<RequestContext> nextHandler) {
            this.nextHandler = nextHandler;
        }

        @Override
        public void handleAdd(final RequestContext requestContext, final AddRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            nextHandler.handleAdd(requestContext, rewrite(request), intermediateResponseHandler,
                    resultHandler);
        }

        @Override
        public void handleBind(final RequestContext requestContext, final int version,
                final BindRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<BindResult> resultHandler) {
            nextHandler.handleBind(requestContext, version, rewrite(request),
                    intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleCompare(final RequestContext requestContext,
                final CompareRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<CompareResult> resultHandler) {
            nextHandler.handleCompare(requestContext, rewrite(request),
                    intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            nextHandler.handleDelete(requestContext, rewrite(request), intermediateResponseHandler,
                    resultHandler);
        }

        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(
                final RequestContext requestContext, final ExtendedRequest<R> request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<R> resultHandler) {
            nextHandler.handleExtendedRequest(requestContext, rewrite(request),
                    intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleModify(final RequestContext requestContext, final ModifyRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            nextHandler.handleModify(requestContext, rewrite(request), intermediateResponseHandler,
                    resultHandler);
        }

        @Override
        public void handleModifyDN(final RequestContext requestContext,
                final ModifyDNRequest request,
                final IntermediateResponseHandler intermediateResponseHandler,
                final LdapResultHandler<Result> resultHandler) {
            nextHandler.handleModifyDN(requestContext, rewrite(request),
                    intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleSearch(final RequestContext requestContext, final SearchRequest request,
            final IntermediateResponseHandler intermediateResponseHandler,
            final SearchResultHandler entryHandler, final LdapResultHandler<Result> resultHandler) {
            nextHandler.handleSearch(requestContext, rewrite(request), intermediateResponseHandler,
                new SearchResultHandler() {
                    @Override
                    public boolean handleReference(SearchResultReference reference) {
                        return entryHandler.handleReference(reference);
                    }

                    @Override
                    public boolean handleEntry(SearchResultEntry entry) {
                        return entryHandler.handleEntry(rewrite(entry));
                    }
                }, resultHandler);
        }

        private AddRequest rewrite(final AddRequest request) {
            // Transform the client DN into a server DN.
            final AddRequest rewrittenRequest = Requests.copyOfAddRequest(request);
            rewrittenRequest.setName(request.getName().toString().replace(CLIENT_SUFFIX,
                    SERVER_SUFFIX));
            /*
             * Transform the client attribute names into server attribute names,
             * fullname;lang-fr ==> cn;lang-fr.
             */
            for (final Attribute a : request.getAllAttributes(clientAttributeDescription)) {
                if (a != null) {
                    final String ad =
                            a.getAttributeDescriptionAsString().replaceFirst(
                                    CLIENT_ATTRIBUTE, SERVER_ATTRIBUTE);
                    final Attribute serverAttr =
                            Attributes.renameAttribute(a, AttributeDescription.valueOf(ad));
                    rewrittenRequest.addAttribute(serverAttr);
                    rewrittenRequest.removeAttribute(a.getAttributeDescription());
                }
            }
            return rewrittenRequest;
        }

        private BindRequest rewrite(final BindRequest request) {
            // TODO: Transform client DN into server DN.
            return request;
        }

        private CompareRequest rewrite(final CompareRequest request) {
            /*
             * Transform the client attribute name into a server attribute name,
             * fullname;lang-fr ==> cn;lang-fr.
             */
            final String ad = request.getAttributeDescription().toString();
            if (ad.toLowerCase().startsWith(CLIENT_ATTRIBUTE.toLowerCase())) {
                final String serverAttrDesc =
                        ad.replaceFirst(CLIENT_ATTRIBUTE, SERVER_ATTRIBUTE);
                request.setAttributeDescription(AttributeDescription.valueOf(serverAttrDesc));
            }

            // Transform the client DN into a server DN.
            return request
                    .setName(request.getName().toString().replace(CLIENT_SUFFIX, SERVER_SUFFIX));
        }

        private DeleteRequest rewrite(final DeleteRequest request) {
            // Transform the client DN into a server DN.
            return request
                    .setName(request.getName().toString().replace(CLIENT_SUFFIX, SERVER_SUFFIX));
        }

        private <S extends ExtendedResult> ExtendedRequest<S> rewrite(
                final ExtendedRequest<S> request) {
            // TODO: Transform password modify, etc.
            return request;
        }

        private ModifyDNRequest rewrite(final ModifyDNRequest request) {
            // Transform the client DNs into server DNs.
            if (request.getNewSuperior() != null) {
                return request.setName(
                        request.getName().toString().replace(CLIENT_SUFFIX, SERVER_SUFFIX))
                        .setNewSuperior(
                                request.getNewSuperior().toString().replace(CLIENT_SUFFIX,
                                        SERVER_SUFFIX));
            } else {
                return request.setName(request.getName().toString().replace(CLIENT_SUFFIX,
                        SERVER_SUFFIX));
            }
        }

        private ModifyRequest rewrite(final ModifyRequest request) {
            // Transform the client DN into a server DN.
            final ModifyRequest rewrittenRequest =
                    Requests.newModifyRequest(request.getName().toString().replace(CLIENT_SUFFIX,
                            SERVER_SUFFIX));

            /*
             * Transform the client attribute names into server attribute names,
             * fullname;lang-fr ==> cn;lang-fr.
             */
            final List<Modification> mods = request.getModifications();
            for (final Modification mod : mods) {
                final Attribute a = mod.getAttribute();
                final AttributeDescription ad = a.getAttributeDescription();
                final AttributeType at = ad.getAttributeType();

                if (at.equals(clientAttributeDescription.getAttributeType())) {
                    final AttributeDescription serverAttrDesc =
                            AttributeDescription.valueOf(ad.toString().replaceFirst(
                                    CLIENT_ATTRIBUTE, SERVER_ATTRIBUTE));
                    rewrittenRequest.addModification(new Modification(mod.getModificationType(),
                            Attributes.renameAttribute(a, serverAttrDesc)));
                } else {
                    rewrittenRequest.addModification(mod);
                }
            }
            for (final Control control : request.getControls()) {
                rewrittenRequest.addControl(control);
            }

            return rewrittenRequest;
        }

        private SearchRequest rewrite(final SearchRequest request) {
            /*
             * Transform the client attribute names to a server attribute names,
             * fullname;lang-fr ==> cn;lang-fr.
             */
            final String[] a = new String[request.getAttributes().size()];
            int count = 0;
            for (final String attrName : request.getAttributes()) {
                if (attrName.toLowerCase().startsWith(CLIENT_ATTRIBUTE.toLowerCase())) {
                    a[count] =
                            attrName.replaceFirst(CLIENT_ATTRIBUTE, SERVER_ATTRIBUTE);
                } else {
                    a[count] = attrName;
                }
                ++count;
            }

            /*
             * Rewrite the baseDN, and rewrite the Filter in dangerously lazy
             * fashion. All the filter rewrite does is a string replace, so if
             * the client attribute name appears in the value part of the AVA,
             * this implementation will not work.
             */
            return Requests.newSearchRequest(DN.valueOf(request.getName().toString().replace(
                    CLIENT_SUFFIX, SERVER_SUFFIX)), request.getScope(), Filter.valueOf(request
                    .getFilter().toString().replace(CLIENT_ATTRIBUTE,
                            SERVER_ATTRIBUTE)), a);
        }

        private SearchResultEntry rewrite(final SearchResultEntry entry) {
            // Replace server attributes with client attributes.
            final Set<Attribute> attrsToAdd = new HashSet<>();
            final Set<AttributeDescription> attrsToRemove = new HashSet<>();

            for (final Attribute a : entry.getAllAttributes(serverAttributeDescription)) {
                final AttributeDescription ad = a.getAttributeDescription();
                final AttributeType at = ad.getAttributeType();
                if (at.equals(serverAttributeDescription.getAttributeType())) {
                    final AttributeDescription clientAttrDesc =
                            AttributeDescription.valueOf(ad.toString().replaceFirst(
                                    SERVER_ATTRIBUTE, CLIENT_ATTRIBUTE));
                    attrsToAdd.add(Attributes.renameAttribute(a, clientAttrDesc));
                    attrsToRemove.add(ad);
                }
            }

            if (!attrsToAdd.isEmpty() && !attrsToRemove.isEmpty()) {
                for (final Attribute a : attrsToAdd) {
                    entry.addAttribute(a);
                }
                for (final AttributeDescription ad : attrsToRemove) {
                    entry.removeAttribute(ad);
                }
            }

            // Transform the server DN suffix into a client DN suffix.
            return entry.setName(entry.getName().toString().replace(SERVER_SUFFIX, CLIENT_SUFFIX));

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
            System.err.println("Usage:" + "\tlocalAddress localPort proxyDN proxyPassword "
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
        final Options factoryOptions = Options.defaultOptions()
                                   .set(LDAPConnectionFactory.AUTHN_BIND_REQUEST,
                                        newSimpleBindRequest(proxyDN, proxyPassword.toCharArray()));
        final ConnectionFactory factory = newCachedConnectionPool(new LDAPConnectionFactory(remoteAddress,
                                                                                            remotePort,
                                                                                            factoryOptions));
        final ConnectionFactory bindFactory = newCachedConnectionPool(new LDAPConnectionFactory(remoteAddress,
                                                                                                remotePort));

        /*
         * Create a server connection adapter which will create a new proxy
         * backend for each inbound client connection. This is required because
         * we need to maintain authorization state between client requests. The
         * proxy bound will be wrapped in a rewriter in order to transform
         * inbound requests and their responses.
         */
        final RequestHandlerFactory<LDAPClientContext, RequestContext> proxyFactory =
                new RequestHandlerFactory<LDAPClientContext, RequestContext>() {
                    @Override
                    public Rewriter handleAccept(final LDAPClientContext clientContext) throws LdapException {
                        return new Rewriter(new ProxyBackend(factory, bindFactory));
                    }
                };
        final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                Connections.newServerConnectionFactory(proxyFactory);

        // Create listener.
        final Options listenerOptions = Options.defaultOptions().set(CONNECT_MAX_BACKLOG, 4096);
        LDAPListener listener = null;
        try {
            listener = new LDAPListener(localAddress, localPort, connectionHandler, listenerOptions);
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
