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
 *      Copyright 2013-2014 ForgeRock AS.
 */
package org.forgerock.opendj.adapter.server3x;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.forgerock.opendj.ldap.AbstractSynchronousConnection;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SASLBindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.forgerock.opendj.ldap.ByteString.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.util.promise.Promises.*;

/**
 * This class provides a connection factory and an adapter for the OpenDJ 2.x
 * server.
 */
public final class Adapters {

    /**
     * Constructor.
     */
    private Adapters() {
        // No implementation required.
    }

    /**
     * Returns a new root connection factory.
     *
     * @return A new root connection factory.
     */
    public static ConnectionFactory newRootConnectionFactory() {
        InternalClientConnection icc = InternalClientConnection.getRootConnection();
        return newConnectionFactory(icc);
    }

    /**
     * Returns a new anonymous connection factory.
     *
     * @return A new anonymous connection factory.
     */
    public static ConnectionFactory newAnonymousConnectionFactory() {
        InternalClientConnection icc = new InternalClientConnection(new AuthenticationInfo());
        return newConnectionFactory(icc);
    }

    /**
     * Returns a new connection factory for a specified user.
     *
     * @param userDN
     *            The specified user's DN.
     * @return a new connection factory.
     */
    public static ConnectionFactory newConnectionFactoryForUser(final DN userDN) {
        InternalClientConnection icc = null;
        try {
            icc = new InternalClientConnection(to(userDN));
        } catch (DirectoryException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return newConnectionFactory(icc);
    }

    /**
     * Returns a new connection factory.
     *
     * @param icc
     *            The internal client connection from server side.
     * @return A new SDK connection factory.
     */
    public static ConnectionFactory newConnectionFactory(final InternalClientConnection icc) {
        return new ConnectionFactory() {

            @Override
            public void close() {
                // Nothing to do.
            }

            @Override
            public Promise<Connection, LdapException> getConnectionAsync() {
                // TODO change the path...
                return newResultPromise(newConnection(icc));
            }

            @Override
            public Connection getConnection() throws LdapException {
                return newConnection(icc);
            }
        };
    }

    /**
     * Returns a new root connection.
     *
     * @return A new root connection.
     */
    public static Connection newRootConnection() {
        return newConnection(InternalClientConnection.getRootConnection());
    }

    /**
     * Returns a new connection for an anonymous user.
     *
     * @return A new connection.
     */
    public static Connection newAnonymousConnection() {
        return newConnection(new InternalClientConnection(new AuthenticationInfo()));
    }

    /**
     * Returns a new connection for a specified user.
     *
     * @param dn
     *            The DN of the user.
     * @return A new connection for a specified user.
     * @throws LdapException
     *             If no such object.
     */
    public static Connection newConnectionForUser(final DN dn) throws LdapException {
        try {
            return newConnection(new InternalClientConnection(to(dn)));
        } catch (DirectoryException e) {
            throw newLdapException(Responses.newResult(ResultCode.NO_SUCH_OBJECT));
        }
    }

    private static Connection newConnection(final InternalClientConnection icc) {
        return new AbstractSynchronousConnection() {

            @Override
            public Result search(final SearchRequest request, final SearchResultHandler handler)
                    throws LdapException {
                InternalSearchListener internalSearchListener = new InternalSearchListener() {

                    @Override
                    public void handleInternalSearchReference(
                            InternalSearchOperation searchOperation,
                            SearchResultReference searchReference) throws DirectoryException {
                        handler.handleReference(from(searchReference));
                    }

                    @Override
                    public void handleInternalSearchEntry(InternalSearchOperation searchOperation,
                            SearchResultEntry searchEntry) throws DirectoryException {
                        handler.handleEntry(from(searchEntry));
                    }
                };

                final SearchFilter filter = toSearchFilter(request.getFilter());
                final org.opends.server.protocols.internal.SearchRequest sr =
                    Requests.newSearchRequest(to(request.getName()), request.getScope(), filter)
                        .setDereferenceAliasesPolicy(request.getDereferenceAliasesPolicy())
                        .setSizeLimit(request.getSizeLimit())
                        .setTimeLimit(request.getTimeLimit())
                        .setTypesOnly(request.isTypesOnly())
                        .addAttribute(request.getAttributes())
                        .addControl(to(request.getControls()));
                return getResponseResult(icc.processSearch(sr, internalSearchListener));
            }

            @Override
            public void removeConnectionEventListener(ConnectionEventListener listener) {
                // Internal client connection don't have any connection events.
            }

            @Override
            public Result modifyDN(final ModifyDNRequest request) throws LdapException {
                final ModifyDNOperation modifyDNOperation =
                        icc.processModifyDN(valueOf(request.getName()),
                                valueOf(request.getNewRDN()),
                                request.isDeleteOldRDN(),
                                request.getNewSuperior() != null ? valueOf(request.getNewSuperior()) : null,
                                to(request.getControls()));
                return getResponseResult(modifyDNOperation);
            }

            @Override
            public Result modify(final ModifyRequest request) throws LdapException {
                final ModifyOperation modifyOperation =
                        icc.processModify(valueOf(request.getName()), toRawModifications(request
                                .getModifications()), to(request.getControls()));
                return getResponseResult(modifyOperation);
            }

            @Override
            public boolean isValid() {
                // Always true.
                return true;
            }

            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
                    final IntermediateResponseHandler handler) throws LdapException {

                final ExtendedOperation extendedOperation =
                        icc.processExtendedOperation(request.getOID(), request.getValue(),
                                to(request.getControls()));

                final Result result = getResponseResult(extendedOperation);
                final GenericExtendedResult genericExtendedResult =
                        Responses.newGenericExtendedResult(result.getResultCode())
                                .setDiagnosticMessage(result.getDiagnosticMessage()).setMatchedDN(
                                        result.getMatchedDN()).setValue(
                                        extendedOperation.getResponseValue().toByteString());
                try {
                    R extendedResult =
                            request.getResultDecoder().decodeExtendedResult(genericExtendedResult,
                                    new DecodeOptions());
                    for (final Control control : result.getControls()) {
                        extendedResult.addControl(control);
                    }
                    return extendedResult;

                } catch (DecodeException e) {
                    org.opends.server.types.DN matchedDN = extendedOperation.getMatchedDN();
                    return request.getResultDecoder().newExtendedErrorResult(
                            extendedOperation.getResultCode(),
                            matchedDN != null ? matchedDN.toString() : null,
                            extendedOperation.getErrorMessage().toString());
                }
            }

            @Override
            public Result delete(final DeleteRequest request) throws LdapException {
                final DeleteOperation deleteOperation =
                        icc.processDelete(valueOf(request.getName()), to(request.getControls()));
                return getResponseResult(deleteOperation);
            }

            @Override
            public CompareResult compare(final CompareRequest request) throws LdapException {
                final CompareOperation compareOperation =
                        icc.processCompare(valueOf(request.getName()),
                                request.getAttributeDescription().toString(),
                                request.getAssertionValue(), to(request.getControls()));

                CompareResult result = Responses.newCompareResult(compareOperation.getResultCode());
                return getResponseResult(compareOperation, result);
            }

            @Override
            public void close(final UnbindRequest request, final String reason) {
                // no implementation in open-ds.
            }

            @Override
            public BindResult bind(final BindRequest request) throws LdapException {
                BindOperation bindOperation = null;
                if (request instanceof SimpleBindRequest) {
                    bindOperation =
                            icc.processSimpleBind(valueOf(request.getName()),
                                    ByteString.wrap(((SimpleBindRequest) request).getPassword()),
                                    to(request.getControls()));
                } else if (request instanceof SASLBindRequest) {
                    String serverName = null;
                    try {
                        serverName = InetAddress.getByName(null).getCanonicalHostName();
                    } catch (UnknownHostException e) {
                        // nothing to do.
                    }
                    BindClient bindClient = request.createBindClient(serverName);
                    do {
                        final GenericBindRequest genericBindRequest = bindClient.nextBindRequest();
                        bindOperation =
                                icc.processSASLBind(
                                        valueOf(request.getName()),
                                        ((SASLBindRequest) request).getSASLMechanism(),
                                        getCredentials(genericBindRequest.getAuthenticationValue()),
                                        to(request.getControls()));
                    } while (bindOperation.getResultCode() == ResultCode.SASL_BIND_IN_PROGRESS);

                    bindClient.dispose();

                } else { // not supported
                    throw newLdapException(Responses.newResult(ResultCode.AUTH_METHOD_NOT_SUPPORTED));
                }
                BindResult result = Responses.newBindResult(bindOperation.getResultCode());
                result.setServerSASLCredentials(bindOperation.getSASLCredentials());

                if (result.isSuccess()) {
                    return result;
                } else {
                    throw newLdapException(result);
                }
            }

            @Override
            public void addConnectionEventListener(ConnectionEventListener listener) {
                // Internal client connection don't have any connection events.
            }

            @Override
            public Result add(final AddRequest request) throws LdapException {
                final AddOperation addOperation =
                        icc.processAdd(valueOf(request.getName()), to(request
                                .getAllAttributes()), to(request.getControls()));
                return getResponseResult(addOperation);
            }

            @Override
            public String toString() {
                return icc.toString();
            }
        };
    }
}
