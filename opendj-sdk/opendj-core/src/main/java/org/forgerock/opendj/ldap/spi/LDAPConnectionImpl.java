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
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import java.io.Closeable;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;

/**
 * LDAP connection interface which implementations of {@link LDAPConnectionFactoryImpl} should implement.
 */
public interface LDAPConnectionImpl extends Closeable {

    /**
     * Abandons the unfinished operation identified in the provided abandon request.
     *
     * @param request
     *         The request identifying the operation to be abandoned.
     * @return A promise whose result is Void.
     * @throws UnsupportedOperationException
     *         If this connection does not support abandon operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#abandonAsync(AbandonRequest)
     */
    LdapPromise<Void> abandonAsync(AbandonRequest request);

    /**
     * Asynchronously adds an entry to the Directory Server using the provided add request.
     *
     * @param request
     *         The add request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support add operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#addAsync(AddRequest, IntermediateResponseHandler)
     */
    LdapPromise<Result> addAsync(AddRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Registers the provided connection event listener so that it will be notified when this connection is closed by
     * the application, receives an unsolicited notification, or experiences a fatal error.
     *
     * @param listener
     *         The listener which wants to be notified when events occur on this connection.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If the {@code listener} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#addConnectionEventListener(ConnectionEventListener)
     */
    void addConnectionEventListener(ConnectionEventListener listener);

    /**
     * Asynchronously authenticates to the Directory Server using the provided bind request.
     *
     * @param request
     *         The bind request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support bind operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#bindAsync(BindRequest, IntermediateResponseHandler)
     */
    LdapPromise<BindResult> bindAsync(BindRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Releases any resources associated with this connection.
     * <p/>
     * Calling {@code close} on a connection that is already closed has no effect.
     * @see org.forgerock.opendj.ldap.Connection#close()
     */
    @Override
    void close();

    /**
     * Releases any resources associated with this connection.
     * <p/>
     * Calling {@code close} on a connection that is already closed has no effect.
     *
     * @param request
     *         The unbind request to use in the case where a physical connection is closed.
     * @param reason
     *         A reason describing why the connection was closed.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#close(UnbindRequest, String)
     */
    void close(UnbindRequest request, String reason);

    /**
     * Asynchronously compares an entry in the Directory Server using the provided compare request.
     *
     * @param request
     *         The compare request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support compare operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#compareAsync(CompareRequest, IntermediateResponseHandler)
     */
    LdapPromise<CompareResult> compareAsync(
            CompareRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Asynchronously deletes an entry from the Directory Server using the provided delete request.
     *
     * @param request
     *         The delete request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support delete operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#deleteAsync(DeleteRequest, IntermediateResponseHandler)
     */
    LdapPromise<Result> deleteAsync(DeleteRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Installs the TLS/SSL security layer on the underlying connection. The TLS/SSL security layer will be installed
     * beneath any existing connection security layers and can only be installed at most once.
     *
     * @param sslContext
     *         The {@code SSLContext} which should be used to secure the
     * @param protocols
     *         Names of all the protocols to enable or {@code null} to use the default protocols.
     * @param suites
     *         Names of all the suites to enable or {@code null} to use the default cipher suites.
     * @return A promise which will complete once the SSL handshake has completed.
     * @throws IllegalStateException
     *         If the TLS/SSL security layer has already been installed.
     */
    Promise<Void, LdapException> enableTLS(SSLContext sslContext, List<String> protocols, List<String> suites);

    /**
     * Asynchronously performs the provided extended request in the Directory Server.
     *
     * @param <R>
     *         The type of result returned by the extended request.
     * @param request
     *         The extended request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support extended operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#extendedRequestAsync(ExtendedRequest, IntermediateResponseHandler)
     */
    <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(
            ExtendedRequest<R> request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Indicates whether or not this connection has been explicitly closed by calling {@code close}. This method will
     * not return {@code true} if a fatal error has occurred on the connection unless {@code close} has been called.
     *
     * @return {@code true} if this connection has been explicitly closed by calling {@code close}, or {@code false}
     * otherwise.
     * @see org.forgerock.opendj.ldap.Connection#isClosed()
     */
    boolean isClosed();

    /**
     * Returns {@code true} if this connection has not been closed and no fatal errors have been detected. This method
     * is guaranteed to return {@code false} only when it is called after the method {@code close} has been called.
     *
     * @return {@code true} if this connection is valid, {@code false} otherwise.
     * @see org.forgerock.opendj.ldap.Connection#isValid()
     */
    boolean isValid();

    /**
     * Asynchronously modifies an entry in the Directory Server using the provided modify request.
     *
     * @param request
     *         The modify request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support modify operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#modifyAsync(ModifyRequest, IntermediateResponseHandler)
     */
    LdapPromise<Result> modifyAsync(ModifyRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Asynchronously renames an entry in the Directory Server using the provided modify DN request.
     *
     * @param request
     *         The modify DN request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#modifyDNAsync(ModifyDNRequest, IntermediateResponseHandler)
     */
    LdapPromise<Result> modifyDNAsync(
            ModifyDNRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Removes the provided connection event listener from this connection so that it will no longer be notified when
     * this connection is closed by the application, receives an unsolicited notification, or experiences a fatal
     * error.
     *
     * @param listener
     *         The listener which no longer wants to be notified when events occur on this connection.
     * @throws NullPointerException
     *         If the {@code listener} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#removeConnectionEventListener(ConnectionEventListener)
     */
    void removeConnectionEventListener(ConnectionEventListener listener);

    /**
     * Asynchronously searches the Directory Server using the provided search request.
     *
     * @param request
     *         The search request.
     * @param intermediateResponseHandler
     *         An intermediate response handler which can be used to process any intermediate responses as they are
     *         received, may be {@code null}.
     * @param entryHandler
     *         A search result handler which can be used to asynchronously process the search result entries and
     *         references as they are received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *         If this connection does not support search operations.
     * @throws IllegalStateException
     *         If this connection has already been closed, i.e. if {@code isClosed() == true}.
     * @throws NullPointerException
     *         If {@code request} was {@code null}.
     * @see org.forgerock.opendj.ldap.Connection#searchAsync(SearchRequest, IntermediateResponseHandler,
     * SearchResultHandler)
     */
    LdapPromise<Result> searchAsync(
            SearchRequest request,
            IntermediateResponseHandler intermediateResponseHandler,
            SearchResultHandler entryHandler);
}
