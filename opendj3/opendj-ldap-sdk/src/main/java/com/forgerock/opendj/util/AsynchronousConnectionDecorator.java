/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.util;



import java.util.Collection;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;
import org.forgerock.opendj.ldap.schema.Schema;



/**
 * A base class from which asynchronous connection decorators may be easily
 * implemented. The default implementation of each method is to delegate to the
 * decorated connection.
 */
public abstract class AsynchronousConnectionDecorator implements
    AsynchronousConnection
{
  /**
   * The decorated asynchronous connection.
   */
  protected final AsynchronousConnection connection;



  /**
   * Creates a new asynchronous connection decorator.
   *
   * @param connection
   *          The asynchronous connection to be decorated.
   */
  protected AsynchronousConnectionDecorator(AsynchronousConnection connection)
  {
    Validator.ensureNotNull(connection);
    this.connection = connection;
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Void> abandon(AbandonRequest request)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.abandon(request);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> add(AddRequest request,
      ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.add(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> add(AddRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.add(request, resultHandler, intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public void addConnectionEventListener(ConnectionEventListener listener)
      throws IllegalStateException, NullPointerException
  {
    connection.addConnectionEventListener(listener);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<BindResult> bind(BindRequest request,
      ResultHandler<? super BindResult> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.bind(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<BindResult> bind(BindRequest request,
      ResultHandler<? super BindResult> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.bind(request, resultHandler, intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public void close()
  {
    connection.close();
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public void close(UnbindRequest request, String reason)
      throws NullPointerException
  {
    connection.close(request, reason);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<CompareResult> compare(CompareRequest request,
      ResultHandler<? super CompareResult> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.compare(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<CompareResult> compare(CompareRequest request,
      ResultHandler<? super CompareResult> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.compare(request, resultHandler,
        intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> delete(DeleteRequest request,
      ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.delete(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> delete(DeleteRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.delete(request, resultHandler,
        intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public <R extends ExtendedResult> FutureResult<R> extendedRequest(
      ExtendedRequest<R> request, ResultHandler<? super R> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.extendedRequest(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public <R extends ExtendedResult> FutureResult<R> extendedRequest(
      ExtendedRequest<R> request, ResultHandler<? super R> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.extendedRequest(request, resultHandler,
        intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to return a synchronous view of this
   * decorated connection.
   */
  public Connection getSynchronousConnection()
  {
    return new SynchronousConnection(this);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public boolean isClosed()
  {
    return connection.isClosed();
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public boolean isValid()
  {
    return connection.isValid();
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> modify(ModifyRequest request,
      ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.modify(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> modify(ModifyRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.modify(request, resultHandler,
        intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> modifyDN(ModifyDNRequest request,
      ResultHandler<? super Result> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.modifyDN(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> modifyDN(ModifyDNRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.modifyDN(request, resultHandler,
        intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<SearchResultEntry> readEntry(DN name,
      Collection<String> attributeDescriptions,
      ResultHandler<? super SearchResultEntry> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.readEntry(name, attributeDescriptions, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<RootDSE> readRootDSE(
      ResultHandler<? super RootDSE> handler)
      throws UnsupportedOperationException, IllegalStateException
  {
    return connection.readRootDSE(handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Schema> readSchema(DN name,
      ResultHandler<? super Schema> handler)
      throws UnsupportedOperationException, IllegalStateException
  {
    return connection.readSchema(name, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Schema> readSchemaForEntry(DN name,
      ResultHandler<? super Schema> handler)
      throws UnsupportedOperationException, IllegalStateException
  {
    return connection.readSchemaForEntry(name, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public void removeConnectionEventListener(ConnectionEventListener listener)
      throws NullPointerException
  {
    connection.removeConnectionEventListener(listener);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> search(SearchRequest request,
      SearchResultHandler handler) throws UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return connection.search(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<Result> search(SearchRequest request,
      SearchResultHandler resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.search(request, resultHandler,
        intermediateResponseHandler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public FutureResult<SearchResultEntry> searchSingleEntry(
      SearchRequest request, ResultHandler<? super SearchResultEntry> handler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    return connection.searchSingleEntry(request, handler);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to delegate.
   */
  public String toString()
  {
    return connection.toString();
  }

}
