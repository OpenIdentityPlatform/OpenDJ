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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.concurrent.BlockingQueue;

import org.opends.sdk.ldif.ConnectionEntryReader;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.schema.Schema;

import com.forgerock.opendj.util.Validator;



/**
 * A {@code SynchronousConnection} adapts an {@code AsynchronousConnection} into
 * a synchronous {@code Connection}.
 */
public class SynchronousConnection extends AbstractConnection
{
  private final AsynchronousConnection connection;



  /**
   * Creates a new abstract connection which will route all synchronous requests
   * to the provided asynchronous connection.
   *
   * @param connection
   *          The asynchronous connection to be used.
   * @throws NullPointerException
   *           If {@code connection} was {@code null}.
   */
  public SynchronousConnection(final AsynchronousConnection connection)
      throws NullPointerException
  {
    Validator.ensureNotNull(connection);
    this.connection = connection;
  }



  /**
   * {@inheritDoc}
   */
  public Result add(final AddRequest request) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    final FutureResult<Result> future = connection.add(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void addConnectionEventListener(final ConnectionEventListener listener)
      throws IllegalStateException, NullPointerException
  {
    connection.addConnectionEventListener(listener);
  }



  /**
   * {@inheritDoc}
   */
  public BindResult bind(final BindRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final FutureResult<BindResult> future = connection.bind(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void close()
  {
    connection.close();
  }



  /**
   * {@inheritDoc}
   */
  public void close(final UnbindRequest request, final String reason)
      throws NullPointerException
  {
    connection.close(request, reason);
  }



  /**
   * {@inheritDoc}
   */
  public CompareResult compare(final CompareRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final FutureResult<CompareResult> future = connection
        .compare(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public Result delete(final DeleteRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final FutureResult<Result> future = connection.delete(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <R extends ExtendedResult> R extendedRequest(
      final ExtendedRequest<R> request) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    final FutureResult<R> future = connection.extendedRequest(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <R extends ExtendedResult> R extendedRequest(
      final ExtendedRequest<R> request,
      final IntermediateResponseHandler handler) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    final FutureResult<R> future = connection.extendedRequest(request, null,
        handler);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public AsynchronousConnection getAsynchronousConnection()
  {
    return connection;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isClosed()
  {
    return connection.isClosed();
  }



  /**
   * {@inheritDoc}
   */
  public boolean isValid()
  {
    return connection.isValid();
  }



  /**
   * {@inheritDoc}
   */
  public Result modify(final ModifyRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final FutureResult<Result> future = connection.modify(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public Result modifyDN(final ModifyDNRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final FutureResult<Result> future = connection.modifyDN(request, null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Schema readSchemaForEntry(final DN name) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException
  {
    final FutureResult<Schema> future = connection.readSchemaForEntry(name,
        null);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void removeConnectionEventListener(
      final ConnectionEventListener listener) throws NullPointerException
  {
    connection.removeConnectionEventListener(listener);
  }



  /**
   * {@inheritDoc}
   */
  public Result search(final SearchRequest request,
      final SearchResultHandler handler) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    final FutureResult<Result> future = connection.search(request, handler);
    try
    {
      return future.get();
    }
    finally
    {
      // Cancel the request if it hasn't completed.
      future.cancel(false);
    }
  }



  /**
   * {@inheritDoc}
   */
  public ConnectionEntryReader search(final SearchRequest request,
      BlockingQueue<Response> entries) throws UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    return new ConnectionEntryReader(getAsynchronousConnection(), request,
        entries);
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return connection.toString();
  }

}
