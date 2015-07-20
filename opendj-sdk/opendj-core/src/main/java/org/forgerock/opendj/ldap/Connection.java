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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import java.io.Closeable;
import java.util.Collection;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
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
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

/**
 * A connection with a Directory Server over which read and update operations
 * may be performed. See RFC 4511 for the LDAPv3 protocol specification and more
 * information about the types of operations defined in LDAP.
 * <p>
 * <h3>Operation processing</h3>
 * <p>
 * Operations may be performed synchronously or asynchronously depending on the
 * method chosen. Asynchronous methods can be identified by their {@code Async}
 * suffix.
 * <p>
 * <h4>Performing operations synchronously</h4>
 * <p>
 * Synchronous methods block until a response is received from the Directory
 * Server, at which point an appropriate {@link Result} object is returned if
 * the operation succeeded, or thrown as an {@link LdapException} if the
 * operation failed.
 * <p>
 * Since synchronous operations block the calling thread, the only way to
 * abandon a long running operation is to interrupt the calling thread from
 * another thread. This will cause the calling thread unblock and throw a
 * {@link CancelledResultException} whose cause is the underlying
 * {@link InterruptedException}.
 * <p>
 * <h4>Performing operations asynchronously</h4>
 * <p>
 * Asynchronous methods, identified by their {@code Async} suffix, are
 * non-blocking, returning a {@link LdapPromise} or sub-type thereof which can
 * be used for retrieving the result using the {@link LdapPromise#get} method.
 * Operation failures, for whatever reason, are signaled by the
 * {@link LdapPromise#get()} method throwing an {@link LdapException}.
 * <p>
 * In addition to returning a {@link LdapPromise}, all asynchronous methods
 * accept a {@link LdapResultHandler} which will be notified upon completion of the
 * operation.
 * <p>
 * Synchronous operations are easily simulated by immediately getting the
 * result:
 *
 * <pre>
 * Connection connection = ...;
 * AddRequest request = ...;
 * // Will block until operation completes, and
 * // throws exception on failure.
 * connection.add(request).get();
 * </pre>
 *
 * Operations can be performed in parallel while taking advantage of the
 * simplicity of a synchronous application design:
 *
 * <pre>
 * Connection connection1 = ...;
 * Connection connection2 = ...;
 * AddRequest request = ...;
 * // Add the entry to the first server (don't block).
 * LdapPromise promise1 = connection1.add(request);
 * // Add the entry to the second server (in parallel).
 * LdapPromise promise2 = connection2.add(request);
 * // Total time = is O(1) instead of O(n).
 * promise1.get();
 * promise2.get();
 * </pre>
 *
 * More complex client applications can take advantage of a fully asynchronous
 * event driven design using {@link LdapResultHandler}s:
 *
 * <pre>
 * Connection connection = ...;
 * SearchRequest request = ...;
 * // Process results in the search result handler
 * // in a separate thread.
 * SearchResponseHandler handle = ...;
 * connection.search(request, handler);
 * </pre>
 * <p>
 * <h3>Closing connections</h3>
 * <p>
 * Applications must ensure that a connection is closed by calling
 * {@link #close()} even if a fatal error occurs on the connection. Once a
 * connection has been closed by the client application, any attempts to
 * continue to use the connection will result in an
 * {@link IllegalStateException} being thrown. Note that, if a fatal error is
 * encountered on the connection, then the application can continue to use the
 * connection. In this case all requests subsequent to the failure will fail
 * with an appropriate {@link LdapException} when their result is
 * retrieved.
 * <p>
 * <h3>Event notification</h3>
 * <p>
 * Applications can choose to be notified when a connection is closed by the
 * application, receives an unsolicited notification, or experiences a fatal
 * error by registering a {@link ConnectionEventListener} with the connection
 * using the {@link #addConnectionEventListener} method.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511">RFC 4511 - Lightweight
 *      Directory Access Protocol (LDAP): The Protocol </a>
 */
public interface Connection extends Closeable {

    /**
     * Abandons the unfinished operation identified in the provided abandon
     * request.
     * <p>
     * Abandon requests do not have a response, so invoking the method get() on
     * the returned promise will not block, nor return anything (it is Void), but
     * may throw an exception if a problem occurred while sending the abandon
     * request. In addition the returned promise may be used in order to
     * determine the message ID of the abandon request.
     * <p>
     * <b>Note:</b> a more convenient approach to abandoning unfinished
     * asynchronous operations is provided via the
     * {@link LdapPromise#cancel(boolean)} method.
     *
     * @param request
     *            The request identifying the operation to be abandoned.
     * @return A promise whose result is Void.
     * @throws UnsupportedOperationException
     *             If this connection does not support abandon operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Void> abandonAsync(AbandonRequest request);

    /**
     * Adds an entry to the Directory Server using the provided add request.
     *
     * @param request
     *            The add request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    Result add(AddRequest request) throws LdapException;

    /**
     * Adds the provided entry to the Directory Server.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * AddRequest request = new AddRequest(entry);
     * connection.add(request);
     * </pre>
     *
     * @param entry
     *            The entry to be added.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code entry} was {@code null} .
     */
    Result add(Entry entry) throws LdapException;

    /**
     * Adds an entry to the Directory Server using the provided lines of LDIF.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * AddRequest request = new AddRequest(ldifLines);
     * connection.add(request);
     * </pre>
     *
     * @param ldifLines
     *            Lines of LDIF containing the an LDIF add change record or an
     *            LDIF entry record.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} was empty, or contained invalid LDIF, or
     *             could not be decoded using the default schema.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null} .
     */
    Result add(String... ldifLines) throws LdapException;

    /**
     * Asynchronously adds an entry to the Directory Server using the provided
     * add request.
     *
     * @param request
     *            The add request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> addAsync(AddRequest request);

    /**
     * Asynchronously adds an entry to the Directory Server using the provided
     * add request.
     *
     * @param request
     *            The add request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support add operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> addAsync(AddRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Registers the provided connection event listener so that it will be
     * notified when this connection is closed by the application, receives an
     * unsolicited notification, or experiences a fatal error.
     *
     * @param listener
     *            The listener which wants to be notified when events occur on
     *            this connection.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code listener} was {@code null}.
     */
    void addConnectionEventListener(ConnectionEventListener listener);

    /**
     * Applies the provided change request to the Directory Server.
     *
     * @param request
     *            The change request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support the provided change
     *             request.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    Result applyChange(ChangeRecord request) throws LdapException;

    /**
     * Asynchronously applies the provided change request to the Directory
     * Server.
     *
     * @param request
     *            The change request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support the provided change
     *             request.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> applyChangeAsync(ChangeRecord request);

    /**
     * Asynchronously applies the provided change request to the Directory
     * Server.
     *
     * @param request
     *            The change request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support the provided change
     *             request.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> applyChangeAsync(ChangeRecord request,
        IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Authenticates to the Directory Server using the provided bind request.
     *
     * @param request
     *            The bind request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support bind operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    BindResult bind(BindRequest request) throws LdapException;

    /**
     * Authenticates to the Directory Server using simple authentication and the
     * provided user name and password.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * BindRequest request = new SimpleBindRequest(name, password);
     * connection.bind(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the Directory object that the client
     *            wishes to bind as, which may be empty.
     * @param password
     *            The password of the Directory object that the client wishes to
     *            bind as, which may be empty.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support bind operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} or {@code password} was {@code null}.
     */
    BindResult bind(String name, char[] password) throws LdapException;

    /**
     * Asynchronously authenticates to the Directory Server using the provided
     * bind request.
     *
     * @param request
     *            The bind request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support bind operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<BindResult> bindAsync(BindRequest request);

    /**
     * Asynchronously authenticates to the Directory Server using the provided
     * bind request.
     *
     * @param request
     *            The bind request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support bind operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<BindResult> bindAsync(BindRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Releases any resources associated with this connection. For physical
     * connections to a Directory Server this will mean that an unbind request
     * is sent and the underlying socket is closed.
     * <p>
     * Other connection implementations may behave differently, and may choose
     * not to send an unbind request if its use is inappropriate (for example a
     * pooled connection will be released and returned to its connection pool
     * without ever issuing an unbind request).
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * UnbindRequest request = new UnbindRequest();
     * connection.close(request);
     * </pre>
     *
     * Calling {@code close} on a connection that is already closed has no
     * effect.
     *
     * @see Connections#uncloseable(Connection)
     */
    @Override
    void close();

    /**
     * Releases any resources associated with this connection. For physical
     * connections to a Directory Server this will mean that the provided unbind
     * request is sent and the underlying socket is closed.
     * <p>
     * Other connection implementations may behave differently, and may choose
     * to ignore the provided unbind request if its use is inappropriate (for
     * example a pooled connection will be released and returned to its
     * connection pool without ever issuing an unbind request).
     * <p>
     * Calling {@code close} on a connection that is already closed has no
     * effect.
     *
     * @param request
     *            The unbind request to use in the case where a physical
     *            connection is closed.
     * @param reason
     *            A reason describing why the connection was closed.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    void close(UnbindRequest request, String reason);

    /**
     * Compares an entry in the Directory Server using the provided compare
     * request.
     *
     * @param request
     *            The compare request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support compare operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    CompareResult compare(CompareRequest request) throws LdapException;

    /**
     * Compares the named entry in the Directory Server against the provided
     * attribute value assertion.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * CompareRequest request = new CompareRequest(name, attributeDescription, assertionValue);
     * connection.compare(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be compared.
     * @param attributeDescription
     *            The name of the attribute to be compared.
     * @param assertionValue
     *            The assertion value to be compared.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} or {@code AttributeDescription} could not be
     *             decoded using the default schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support compare operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name}, {@code attributeDescription}, or
     *             {@code assertionValue} was {@code null}.
     */
    CompareResult compare(String name, String attributeDescription, String assertionValue)
            throws LdapException;

    /**
     * Asynchronously compares an entry in the Directory Server using the
     * provided compare request.
     *
     * @param request
     *            The compare request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support compare operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<CompareResult> compareAsync(CompareRequest request);

    /**
     * Asynchronously compares an entry in the Directory Server using the
     * provided compare request.
     *
     * @param request
     *            The compare request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support compare operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<CompareResult> compareAsync(CompareRequest request,
        IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Deletes an entry from the Directory Server using the provided delete
     * request.
     *
     * @param request
     *            The delete request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    Result delete(DeleteRequest request) throws LdapException;

    /**
     * Deletes the named entry from the Directory Server.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * DeleteRequest request = new DeleteRequest(name);
     * connection.delete(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be deleted.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    Result delete(String name) throws LdapException;

    /**
     * Deletes the named entry and all of its subordinates from the Directory
     * Server.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * DeleteRequest request = new DeleteRequest(name).addControl(
     * connection.delete(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the subtree base entry to be
     *            deleted.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} was {@code null}.
     */
    Result deleteSubtree(String name) throws LdapException;

    /**
     * Asynchronously deletes an entry from the Directory Server using the
     * provided delete request.
     *
     * @param request
     *            The delete request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> deleteAsync(DeleteRequest request);

    /**
     * Asynchronously deletes an entry from the Directory Server using the
     * provided delete request.
     *
     * @param request
     *            The delete request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support delete operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> deleteAsync(DeleteRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Requests that the Directory Server performs the provided extended
     * request.
     *
     * @param <R>
     *            The type of result returned by the extended request.
     * @param request
     *            The extended request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support extended operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    <R extends ExtendedResult> R extendedRequest(ExtendedRequest<R> request) throws LdapException;

    /**
     * Requests that the Directory Server performs the provided extended
     * request, optionally listening for any intermediate responses.
     *
     * @param <R>
     *            The type of result returned by the extended request.
     * @param request
     *            The extended request.
     * @param handler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support extended operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    <R extends ExtendedResult> R extendedRequest(ExtendedRequest<R> request, IntermediateResponseHandler handler)
            throws LdapException;

    /**
     * Requests that the Directory Server performs the provided extended
     * request.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * GenericExtendedRequest request = new GenericExtendedRequest(requestName, requestValue);
     * connection.extendedRequest(request);
     * </pre>
     *
     * @param requestName
     *            The dotted-decimal representation of the unique OID
     *            corresponding to the extended request.
     * @param requestValue
     *            The content of the extended request in a form defined by the
     *            extended operation, or {@code null} if there is no content.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support extended operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code requestName} was {@code null}.
     */
    GenericExtendedResult extendedRequest(String requestName, ByteString requestValue) throws LdapException;

    /**
     * Asynchronously performs the provided extended request in the Directory
     * Server.
     *
     * @param <R>
     *            The type of result returned by the extended request.
     * @param request
     *            The extended request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support extended operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request);

    /**
     * Asynchronously performs the provided extended request in the Directory
     * Server.
     *
     * @param <R>
     *            The type of result returned by the extended request.
     * @param request
     *            The extended request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support extended operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request,
        IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Indicates whether or not this connection has been explicitly closed by
     * calling {@code close}. This method will not return {@code true} if a
     * fatal error has occurred on the connection unless {@code close} has been
     * called.
     *
     * @return {@code true} if this connection has been explicitly closed by
     *         calling {@code close}, or {@code false} otherwise.
     */
    boolean isClosed();

    /**
     * Returns {@code true} if this connection has not been closed and no fatal
     * errors have been detected. This method is guaranteed to return
     * {@code false} only when it is called after the method {@code close} has
     * been called.
     *
     * @return {@code true} if this connection is valid, {@code false}
     *         otherwise.
     */
    boolean isValid();

    /**
     * Modifies an entry in the Directory Server using the provided modify
     * request.
     *
     * @param request
     *            The modify request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    Result modify(ModifyRequest request) throws LdapException;

    /**
     * Modifies an entry in the Directory Server using the provided lines of
     * LDIF.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * ModifyRequest request = new ModifyRequest(name, ldifChanges);
     * connection.modify(request);
     * </pre>
     *
     * @param ldifLines
     *            Lines of LDIF containing the a single LDIF modify change
     *            record.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify operations.
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} was empty, or contained invalid LDIF, or
     *             could not be decoded using the default schema.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null} .
     */
    Result modify(String... ldifLines) throws LdapException;

    /**
     * Asynchronously modifies an entry in the Directory Server using the
     * provided modify request.
     *
     * @param request
     *            The modify request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> modifyAsync(ModifyRequest request);

    /**
     * Asynchronously modifies an entry in the Directory Server using the
     * provided modify request.
     *
     * @param request
     *            The modify request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> modifyAsync(ModifyRequest request, IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Renames an entry in the Directory Server using the provided modify DN
     * request.
     *
     * @param request
     *            The modify DN request.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    Result modifyDN(ModifyDNRequest request) throws LdapException;

    /**
     * Renames the named entry in the Directory Server using the provided new
     * RDN.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * ModifyDNRequest request = new ModifyDNRequest(name, newRDN);
     * connection.modifyDN(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be renamed.
     * @param newRDN
     *            The new RDN of the entry.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code name} or {@code newRDN} could not be decoded using
     *             the default schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code name} or {@code newRDN} was {@code null}.
     */
    Result modifyDN(String name, String newRDN) throws LdapException;

    /**
     * Asynchronously renames an entry in the Directory Server using the
     * provided modify DN request.
     *
     * @param request
     *            The modify DN request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> modifyDNAsync(ModifyDNRequest request);

    /**
     * Asynchronously renames an entry in the Directory Server using the
     * provided modify DN request.
     *
     * @param request
     *            The modify DN request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support modify DN operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> modifyDNAsync(ModifyDNRequest request,
        IntermediateResponseHandler intermediateResponseHandler);

    /**
     * Reads the named entry from the Directory Server.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request = new SearchRequest(name, SearchScope.BASE_OBJECT,
     * &quot;(objectClass=*)&quot;, attributeDescriptions);
     * connection.searchSingleEntry(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be read.
     * @param attributeDescriptions
     *            The names of the attributes to be included with the entry,
     *            which may be {@code null} or empty indicating that all user
     *            attributes should be returned.
     * @return The single search result entry returned from the search.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code name} was {@code null}.
     */
    SearchResultEntry readEntry(DN name, String... attributeDescriptions) throws LdapException;

    /**
     * Reads the named entry from the Directory Server.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request =
     *         new SearchRequest(name, SearchScope.BASE_OBJECT, &quot;(objectClass=*)&quot;, attributeDescriptions);
     * connection.searchSingleEntry(request);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be read.
     * @param attributeDescriptions
     *            The names of the attributes to be included with the entry.
     * @return The single search result entry returned from the search.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code baseObject} could not be decoded using the default
     *             schema.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code name} was {@code null}.
     */
    SearchResultEntry readEntry(String name, String... attributeDescriptions) throws LdapException;

    /**
     * Asynchronously reads the named entry from the Directory Server.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, the returned promise will never return {@code null}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request =
     *         new SearchRequest(name, SearchScope.BASE_OBJECT, &quot;(objectClass=*)&quot;, attributeDescriptions);
     * connection.searchSingleEntryAsync(request, resultHandler, p);
     * </pre>
     *
     * @param name
     *            The distinguished name of the entry to be read.
     * @param attributeDescriptions
     *            The names of the attributes to be included with the entry,
     *            which may be {@code null} or empty indicating that all user
     *            attributes should be returned.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code name} was {@code null}.
     */
    LdapPromise<SearchResultEntry> readEntryAsync(DN name, Collection<String> attributeDescriptions);

    /**
     * Removes the provided connection event listener from this connection so
     * that it will no longer be notified when this connection is closed by the
     * application, receives an unsolicited notification, or experiences a fatal
     * error.
     *
     * @param listener
     *            The listener which no longer wants to be notified when events
     *            occur on this connection.
     * @throws NullPointerException
     *             If the {@code listener} was {@code null}.
     */
    void removeConnectionEventListener(ConnectionEventListener listener);

    /**
     * Searches the Directory Server using the provided search parameters. Any
     * matching entries returned by the search will be exposed through the
     * returned {@code ConnectionEntryReader}.
     * <p>
     * Unless otherwise specified, calling this method is equivalent to:
     *
     * <pre>
     * ConnectionEntryReader reader = new ConnectionEntryReader(this, request);
     * </pre>
     *
     * @param request
     *            The search request.
     * @return The result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} or {@code entries} was {@code null}.
     */
    ConnectionEntryReader search(SearchRequest request);

    /**
     * Searches the Directory Server using the provided search request. Any
     * matching entries returned by the search will be added to {@code entries},
     * even if the final search result indicates that the search failed. Search
     * result references will be discarded.
     * <p>
     * <b>Warning:</b> Usage of this method is discouraged if the search request
     * is expected to yield a large number of search results since the entire
     * set of results will be stored in memory, potentially causing an
     * {@code OutOfMemoryError}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * connection.search(request, entries, null);
     * </pre>
     *
     * @param request
     *            The search request.
     * @param entries
     *            The collection to which matching entries should be added.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} or {@code entries} was {@code null}.
     */
    Result search(SearchRequest request, Collection<? super SearchResultEntry> entries) throws LdapException;

    /**
     * Searches the Directory Server using the provided search request. Any
     * matching entries returned by the search will be added to {@code entries},
     * even if the final search result indicates that the search failed.
     * Similarly, search result references returned by the search will be added
     * to {@code references}.
     * <p>
     * <b>Warning:</b> Usage of this method is discouraged if the search request
     * is expected to yield a large number of search results since the entire
     * set of results will be stored in memory, potentially causing an
     * {@code OutOfMemoryError}.
     *
     * @param request
     *            The search request.
     * @param entries
     *            The collection to which matching entries should be added.
     * @param references
     *            The collection to which search result references should be
     *            added, or {@code null} if references are to be discarded.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} or {@code entries} was {@code null}.
     */
    Result search(SearchRequest request, Collection<? super SearchResultEntry> entries,
            Collection<? super SearchResultReference> references) throws LdapException;

    /**
     * Searches the Directory Server using the provided search request. Any
     * matching entries returned by the search as well as any search result
     * references will be passed to the provided search result handler.
     *
     * @param request
     *            The search request.
     * @param handler
     *            A search result handler which can be used to process the
     *            search result entries and references as they are received, may
     *            be {@code null}.
     * @return The result of the operation.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    Result search(SearchRequest request, SearchResultHandler handler) throws LdapException;

    /**
     * Searches the Directory Server using the provided search parameters. Any
     * matching entries returned by the search will be exposed through the
     * {@code EntryReader} interface.
     * <p>
     * <b>Warning:</b> When using a queue with an optional capacity bound, the
     * connection will stop reading responses and wait if necessary for space to
     * become available.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request = new SearchRequest(baseDN, scope, filter, attributeDescriptions);
     * connection.search(request, new LinkedBlockingQueue&lt;Response&gt;());
     * </pre>
     *
     * @param baseObject
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @param scope
     *            The scope of the search.
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     * @return An entry reader exposing the returned entries.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code baseObject}, {@code scope}, or {@code filter}
     *             were {@code null}.
     */
    ConnectionEntryReader search(String baseObject, SearchScope scope, String filter,
            String... attributeDescriptions);

    /**
     * Asynchronously searches the Directory Server using the provided search
     * request.
     *
     * @param request
     *            The search request.
     * @param entryHandler
     *            A search result handler which can be used to asynchronously
     *            process the search result entries and references as they are
     *            received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> searchAsync(SearchRequest request, SearchResultHandler entryHandler);

    /**
     * Asynchronously searches the Directory Server using the provided search
     * request.
     *
     * @param request
     *            The search request.
     * @param intermediateResponseHandler
     *            An intermediate response handler which can be used to process
     *            any intermediate responses as they are received, may be
     *            {@code null}.
     * @param entryHandler
     *            A search result handler which can be used to asynchronously
     *            process the search result entries and references as they are
     *            received, may be {@code null}.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If {@code request} was {@code null}.
     */
    LdapPromise<Result> searchAsync(SearchRequest request, IntermediateResponseHandler intermediateResponseHandler,
        SearchResultHandler entryHandler);

    /**
     * Searches the Directory Server for a single entry using the provided
     * search request.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}. If multiple
     * matching entries are returned by the Directory Server then the request
     * will fail with an {@link MultipleEntriesFoundException}.
     *
     * @param request
     *            The search request.
     * @return The single search result entry returned from the search.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code request} was {@code null}.
     */
    SearchResultEntry searchSingleEntry(SearchRequest request) throws LdapException;

    /**
     * Searches the Directory Server for a single entry using the provided
     * search parameters.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}. If multiple
     * matching entries are returned by the Directory Server then the request
     * will fail with an {@link MultipleEntriesFoundException}.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * SearchRequest request = new SearchRequest(baseObject, scope, filter, attributeDescriptions);
     * connection.searchSingleEntry(request);
     * </pre>
     *
     * @param baseObject
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed.
     * @param scope
     *            The scope of the search.
     * @param filter
     *            The filter that defines the conditions that must be fulfilled
     *            in order for an entry to be returned.
     * @param attributeDescriptions
     *            The names of the attributes to be included with each entry.
     * @return The single search result entry returned from the search.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws LocalizedIllegalArgumentException
     *             If {@code baseObject} could not be decoded using the default
     *             schema or if {@code filter} is not a valid LDAP string
     *             representation of a filter.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code baseObject}, {@code scope}, or {@code filter}
     *             were {@code null}.
     */
    SearchResultEntry searchSingleEntry(String baseObject, SearchScope scope, String filter,
            String... attributeDescriptions) throws LdapException;

    /**
     * Asynchronously searches the Directory Server for a single entry using the
     * provided search request.
     * <p>
     * If the requested entry is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, the returned promise will never return {@code null}. If
     * multiple matching entries are returned by the Directory Server then the
     * request will fail with an {@link MultipleEntriesFoundException}.
     *
     * @param request
     *            The search request.
     * @return A promise representing the result of the operation.
     * @throws UnsupportedOperationException
     *             If this connection does not support search operations.
     * @throws IllegalStateException
     *             If this connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code request} was {@code null}.
     */
    LdapPromise<SearchResultEntry> searchSingleEntryAsync(SearchRequest request);
}
