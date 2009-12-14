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



import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.Result;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * A {@code SynchronousConnection} adapts an {@code
 * AsynchronousConnection} into a synchronous {@code Connection}.
 */
public class SynchronousConnection extends AbstractConnection
{
  private final AsynchronousConnection connection;



  /**
   * Creates a new abstract connection which will route all synchronous
   * requests to the provided asynchronous connection.
   *
   * @param connection
   *          The asynchronous connection to be used.
   * @throws NullPointerException
   *           If {@code connection} was {@code null}.
   */
  public SynchronousConnection(AsynchronousConnection connection)
      throws NullPointerException
  {
    Validator.ensureNotNull(connection);
    this.connection = connection;
  }



  /**
   * {@inheritDoc}
   */
  public Result add(AddRequest request) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    ResultFuture<Result> future = connection.add(request, null);
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



  public void addConnectionEventListener(
      ConnectionEventListener listener) throws IllegalStateException,
      NullPointerException
  {
    connection.addConnectionEventListener(listener);
  }



  public BindResult bind(BindRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    ResultFuture<BindResult> future = connection.bind(request, null);
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



  public void close()
  {
    connection.close();
  }



  public void close(UnbindRequest request, String reason)
      throws NullPointerException
  {
    connection.close(request, reason);
  }



  public CompareResult compare(CompareRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    ResultFuture<CompareResult> future = connection.compare(request,
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



  public Result delete(DeleteRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    ResultFuture<Result> future = connection.delete(request, null);
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



  public <R extends Result> R extendedRequest(ExtendedRequest<R> request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    ResultFuture<R> future = connection.extendedRequest(request, null);
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



  public Result modify(ModifyRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    ResultFuture<Result> future = connection.modify(request, null);
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



  public Result modifyDN(ModifyDNRequest request)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    ResultFuture<Result> future = connection.modifyDN(request, null);
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



  public void removeConnectionEventListener(
      ConnectionEventListener listener) throws NullPointerException
  {
    connection.removeConnectionEventListener(listener);
  }



  /**
   * {@inheritDoc}
   */
  public Result search(SearchRequest request,
      SearchResultHandler handler) throws ErrorResultException,
      InterruptedException, UnsupportedOperationException,
      IllegalStateException, NullPointerException
  {
    ResultFuture<Result> future = connection.search(request, null,
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
  public boolean isClosed()
  {
    return connection.isClosed();
  }



  /**
   * {@inheritDoc}
   */
  public Schema readSchemaForEntry(DN name)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException
  {
    ResultFuture<Schema> future = connection.readSchemaForEntry(name,
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

}
