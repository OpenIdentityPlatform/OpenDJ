/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.schema.Schema;



/**
 * An asynchronous connection with a Directory Server over which read
 * and update operations may be performed. See RFC 4511 for the LDAPv3
 * protocol specification and more information about the types of
 * operations defined in LDAP.
 * <p>
 * <h3>Operation processing</h3>
 * <p>
 * All operations are performed asynchronously and return a
 * {@link FutureResult} or sub-type thereof which can be used for
 * retrieving the result using the {@link FutureResult#get} method.
 * Operation failures, for whatever reason, are signalled by the
 * {@link FutureResult#get()} method throwing an
 * {@link ErrorResultException}.
 * <p>
 * Synchronous operations are easily simulated by immediately getting
 * the result:
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
 * FutureResult future1 = connection1.add(request);
 * // Add the entry to the second server (in parallel).
 * FutureResult future2 = connection2.add(request);
 * // Total time = is O(1) instead of O(n).
 * future1.get();
 * future2.get();
 * </pre>
 *
 * More complex client applications can take advantage of a fully
 * asynchronous event driven design using {@link ResultHandler}s:
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
 * {@link #close()} even if a fatal error occurs on the connection. Once
 * a connection has been closed by the client application, any attempts
 * to continue to use the connection will result in an
 * {@link IllegalStateException} being thrown. Note that, if a fatal
 * error is encountered on the connection, then the application can
 * continue to use the connection. In this case all requests subsequent
 * to the failure will fail with an appropriate
 * {@link ErrorResultException} when their result is retrieved.
 * <p>
 * <h3>Event notification</h3>
 * <p>
 * Applications can choose to be notified when a connection is closed by
 * the application, receives an unsolicited notification, or experiences
 * a fatal error by registering a {@link ConnectionEventListener} with
 * the connection using the {@link #addConnectionEventListener} method.
 * <p>
 * <h3>TO DO</h3>
 * <p>
 * <ul>
 * <li>do we need isClosed() and isValid()?
 * <li>do we need connection event notification of client close? JDBC
 * and JCA have this functionality in their pooled (managed) connection
 * APIs. We need some form of event notification at the app level for
 * unsolicited notifications.
 * <li>method for performing update operation (e.g. LDIF change
 * records).
 * <li>should unsupported methods throw UnsupportedOperationException or
 * throw an ErrorResultException using an UnwillingToPerform result code
 * (or something similar)?
 * <li>Implementations should indicate whether or not they are thread
 * safe and support concurrent requests.
 * </ul>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511">RFC 4511 -
 *      Lightweight Directory Access Protocol (LDAP): The Protocol </a>
 */
public interface AsynchronousConnection extends Closeable
{

  /**
   * Abandons the unfinished operation identified in the provided
   * abandon request.
   * <p>
   * <b>Note:</b> a more convenient approach to abandoning unfinished
   * operations is provided via the {@link FutureResult#cancel(boolean)}
   * method.
   *
   * @param request
   *          The request identifying the operation to be abandoned.
   * @throws UnsupportedOperationException
   *           If this connection does not support abandon operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  void abandon(AbandonRequest request)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Adds an entry to the Directory Server using the provided add
   * request.
   *
   * @param request
   *          The add request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support add operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<Result> add(AddRequest request,
      ResultHandler<Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Registers the provided connection event listener so that it will be
   * notified when this connection is closed by the application,
   * receives an unsolicited notification, or experiences a fatal error.
   *
   * @param listener
   *          The listener which wants to be notified when events occur
   *          on this connection.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code listener} was {@code null}.
   */
  void addConnectionEventListener(ConnectionEventListener listener)
      throws IllegalStateException, NullPointerException;



  /**
   * Authenticates to the Directory Server using the provided bind
   * request.
   *
   * @param request
   *          The bind request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support bind operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<BindResult> bind(BindRequest request,
      ResultHandler<? super BindResult> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Releases any resources associated with this connection. For
   * physical connections to a Directory Server this will mean that an
   * unbind request is sent and the underlying socket is closed.
   * <p>
   * Other connection implementations may behave differently, and may
   * choose not to send an unbind request if its use is inappropriate
   * (for example a pooled connection will be released and returned to
   * its connection pool without ever issuing an unbind request).
   * <p>
   * This method is semantically equivalent to the following code:
   *
   * <pre>
   * UnbindRequest request = Requests.newUnbindRequest();
   * connection.close(request);
   * </pre>
   *
   * Calling {@code close} on a connection that is already closed has no
   * effect.
   */
  void close();



  /**
   * Releases any resources associated with this connection. For
   * physical connections to a Directory Server this will mean that the
   * provided unbind request is sent and the underlying socket is
   * closed.
   * <p>
   * Other connection implementations may behave differently, and may
   * choose to ignore the provided unbind request if its use is
   * inappropriate (for example a pooled connection will be released and
   * returned to its connection pool without ever issuing an unbind
   * request).
   * <p>
   * Calling {@code close} on a connection that is already closed has no
   * effect.
   *
   * @param request
   *          The unbind request to use in the case where a physical
   *          connection is closed.
   * @param reason
   *          A reason describing why the connection was closed.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  void close(UnbindRequest request, String reason);



  /**
   * Compares an entry in the Directory Server using the provided
   * compare request.
   *
   * @param request
   *          The compare request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support compare operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<CompareResult> compare(CompareRequest request,
      ResultHandler<? super CompareResult> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Deletes an entry from the Directory Server using the provided
   * delete request.
   *
   * @param request
   *          The delete request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support delete operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<Result> delete(DeleteRequest request,
      ResultHandler<Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Requests that the Directory Server performs the provided extended
   * request.
   *
   * @param <R>
   *          The type of result returned by the extended request.
   * @param request
   *          The extended request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support extended operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  <R extends Result> FutureResult<R> extendedRequest(
      ExtendedRequest<R> request, ResultHandler<? super R> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Indicates whether or not this connection has been explicitly closed
   * by calling {@code close}. This method will not return {@code true}
   * if a fatal error has occurred on the connection unless {@code
   * close} has been called.
   *
   * @return {@code true} if this connection has been explicitly closed
   *         by calling {@code close}, or {@code false} otherwise.
   */
  boolean isClosed();



  /**
   * Returns true if the connection has not been closed and is still valid.
   * The implementation shall submit a search on the connection or use some
   * other mechanism that positively verifies the connection is still valid
   * when this method is called.
   *
   * The query submitted by the driver to validate the connection shall be
   * executed in the authentication context.
   *
   * @return {@code true} if the connection is valid, {@code false} otherwise.
   */
  boolean isValid();


  
  /**
   * Modifies an entry in the Directory Server using the provided modify
   * request.
   *
   * @param request
   *          The modify request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support modify operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<Result> modify(ModifyRequest request,
      ResultHandler<Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Renames an entry in the Directory Server using the provided modify
   * DN request.
   *
   * @param request
   *          The modify DN request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support modify DN operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<Result> modifyDN(ModifyDNRequest request,
      ResultHandler<Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Reads the named entry from the Directory Server.
   * <p>
   * If the requested entry is not returned by the Directory Server then
   * the request will fail with an {@link EntryNotFoundException}. More
   * specifically, the returned future will never return {@code null}.
   * <p>
   * This method is equivalent to the following code:
   *
   * <pre>
   * SearchRequest request = new SearchRequest(name,
   *     SearchScope.BASE_OBJECT, &quot;(objectClass=*)&quot;, attributeDescriptions);
   * connection.searchSingleEntry(request, resultHandler, p);
   * </pre>
   *
   * @param name
   *          The distinguished name of the entry to be read.
   * @param attributeDescriptions
   *          The names of the attributes to be included with the entry,
   *          which may be {@code null} or empty indicating that all
   *          user attributes should be returned.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code name} was {@code null}.
   */
  FutureResult<SearchResultEntry> readEntry(DN name,
      Collection<String> attributeDescriptions,
      ResultHandler<? super SearchResultEntry> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Reads the Root DSE from the Directory Server.
   * <p>
   * If the Root DSE is not returned by the Directory Server then the
   * request will fail with an {@link EntryNotFoundException}. More
   * specifically, the returned future will never return {@code null}.
   *
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   */
  FutureResult<RootDSE> readRootDSE(ResultHandler<RootDSE> handler)
      throws UnsupportedOperationException, IllegalStateException;



  /**
   * Reads the schema from the Directory Server contained in the named
   * subschema sub-entry.
   * <p>
   * If the requested schema is not returned by the Directory Server
   * then the request will fail with an {@link EntryNotFoundException}.
   * More specifically, the returned future will never return {@code
   * null}.
   * <p>
   * Implementations may choose to perform optimizations such as
   * caching.
   *
   * @param name
   *          The distinguished name of the subschema sub-entry.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   */
  FutureResult<Schema> readSchema(DN name, ResultHandler<Schema> handler)
      throws UnsupportedOperationException, IllegalStateException;



  /**
   * Reads the schema from the Directory Server which applies to the
   * named entry.
   * <p>
   * If the requested entry or its associated schema are not returned by
   * the Directory Server then the request will fail with an
   * {@link EntryNotFoundException}. More specifically, the returned
   * future will never return {@code null}.
   * <p>
   * A typical implementation will first read the {@code
   * subschemaSubentry} attribute of the entry in order to locate the
   * schema. However, implementations may choose to perform other
   * optimizations, such as caching.
   *
   * @param name
   *          The distinguished name of the entry whose schema is to be
   *          located.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}. Optional additional handler parameter.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   */
  FutureResult<Schema> readSchemaForEntry(DN name,
      ResultHandler<Schema> handler)
      throws UnsupportedOperationException, IllegalStateException;



  /**
   * Removes the provided connection event listener from this connection
   * so that it will no longer be notified when this connection is
   * closed by the application, receives an unsolicited notification, or
   * experiences a fatal error.
   *
   * @param listener
   *          The listener which no longer wants to be notified when
   *          events occur on this connection.
   * @throws NullPointerException
   *           If the {@code listener} was {@code null}.
   */
  void removeConnectionEventListener(ConnectionEventListener listener)
      throws NullPointerException;



  /**
   * Searches the Directory Server using the provided search request.
   *
   * @param request
   *          The search request.
   * @param resultHandler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @param searchResulthandler
   *          A search result handler which can be used to
   *          asynchronously process the search result entries and
   *          references as they are received, may be {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If {@code request} was {@code null}.
   */
  FutureResult<Result> search(SearchRequest request,
      ResultHandler<Result> resultHandler,
      SearchResultHandler searchResulthandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;



  /**
   * Searches the Directory Server for a single entry using the provided
   * search request.
   * <p>
   * If the requested entry is not returned by the Directory Server then
   * the request will fail with an {@link EntryNotFoundException}. More
   * specifically, the returned future will never return {@code null}.
   * If multiple matching entries are returned by the Directory Server
   * then the request will fail with an
   * {@link MultipleEntriesFoundException}.
   *
   * @param request
   *          The search request.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code request} was {@code null}.
   */
  FutureResult<SearchResultEntry> searchSingleEntry(
      SearchRequest request,
      ResultHandler<? super SearchResultEntry> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException;
}
