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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap;



import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;

import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.Validator;



/**
 * This class defines a pseudo-connection object that can be used for performing
 * internal operations directly against a {@code ServerConnection}
 * implementation.
 */
public final class InternalConnection extends AbstractAsynchronousConnection
{
  private static final class InternalBindFutureResultImpl extends
      AbstractLDAPFutureResultImpl<BindResult>
  {
    private final BindRequest bindRequest;



    InternalBindFutureResultImpl(final int messageID,
        final BindRequest bindRequest,
        final ResultHandler<? super BindResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler,
        final AsynchronousConnection connection)
    {
      super(messageID, resultHandler, intermediateResponseHandler, connection);
      this.bindRequest = bindRequest;
    }



    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder();
      sb.append("InternalBindFutureResultImpl(");
      sb.append("bindRequest = ");
      sb.append(bindRequest);
      super.toString(sb);
      sb.append(")");
      return sb.toString();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    BindResult newErrorResult(final ResultCode resultCode,
        final String diagnosticMessage, final Throwable cause)
    {
      return Responses.newBindResult(resultCode)
          .setDiagnosticMessage(diagnosticMessage).setCause(cause);
    }
  }



  private final ServerConnection<Integer> serverConnection;
  private final List<ConnectionEventListener> listeners =
    new CopyOnWriteArrayList<ConnectionEventListener>();
  private final AtomicInteger messageID = new AtomicInteger();



  /**
   * Sets the server connection associated with this internal connection.
   *
   * @param serverConnection
   *          The server connection.
   */
  public InternalConnection(final ServerConnection<Integer> serverConnection)
  {
    this.serverConnection = serverConnection;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Void> abandon(final AbandonRequest request)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    serverConnection.handleAbandon(i, request);
    return new CompletedFutureResult<Void>((Void) null, i);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Result> add(final AddRequest request,
      final ResultHandler<? super Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request,
        resultHandler, intermediateResponseHandler, this);
    serverConnection.handleAdd(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void addConnectionEventListener(final ConnectionEventListener listener)
      throws IllegalStateException, NullPointerException
  {
    Validator.ensureNotNull(listener);
    listeners.add(listener);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<BindResult> bind(final BindRequest request,
      final ResultHandler<? super BindResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final InternalBindFutureResultImpl future = new InternalBindFutureResultImpl(
        i, request, resultHandler, intermediateResponseHandler, this);
    serverConnection.handleBind(i, 3, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void close(final UnbindRequest request, final String reason)
  {
    final int i = messageID.getAndIncrement();
    serverConnection.handleConnectionClosed(i, request);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<CompareResult> compare(final CompareRequest request,
      final ResultHandler<? super CompareResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPCompareFutureResultImpl future = new LDAPCompareFutureResultImpl(
        i, request, resultHandler, intermediateResponseHandler, this);
    serverConnection.handleCompare(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Result> delete(final DeleteRequest request,
      final ResultHandler<? super Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request,
        resultHandler, intermediateResponseHandler, this);
    serverConnection.handleDelete(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R extends ExtendedResult> FutureResult<R> extendedRequest(
      final ExtendedRequest<R> request,
      final ResultHandler<? super R> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPExtendedFutureResultImpl<R> future = new LDAPExtendedFutureResultImpl<R>(
        i, request, resultHandler, intermediateResponseHandler, this);
    serverConnection.handleExtendedRequest(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClosed()
  {
    // FIXME: this should be true after close has been called.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isValid()
  {
    // FIXME: this should be false if this connection is disconnected.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Result> modify(final ModifyRequest request,
      final ResultHandler<? super Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request,
        resultHandler, intermediateResponseHandler, this);
    serverConnection.handleModify(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Result> modifyDN(final ModifyDNRequest request,
      final ResultHandler<? super Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(i, request,
        resultHandler, intermediateResponseHandler, this);
    serverConnection.handleModifyDN(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void removeConnectionEventListener(
      final ConnectionEventListener listener) throws NullPointerException
  {
    Validator.ensureNotNull(listener);
    listeners.remove(listener);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public FutureResult<Result> search(final SearchRequest request,
      final SearchResultHandler resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final int i = messageID.getAndIncrement();
    final LDAPSearchFutureResultImpl future = new LDAPSearchFutureResultImpl(i,
        request, resultHandler, intermediateResponseHandler, this);
    serverConnection.handleSearch(i, request, future, future);
    return future;
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("InternalConnection(");
    builder.append(String.valueOf(serverConnection));
    builder.append(')');
    return builder.toString();
  }

}
