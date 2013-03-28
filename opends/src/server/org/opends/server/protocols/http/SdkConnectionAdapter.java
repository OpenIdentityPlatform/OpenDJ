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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import static org.forgerock.opendj.adapter.server2x.Converters.*;
import static org.forgerock.opendj.ldap.ByteString.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.LinkedHashSet;

import org.forgerock.opendj.ldap.AbstractAsynchronousConnection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
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
import org.opends.server.core.QueueingStrategy;
import org.opends.server.core.SearchOperationBasis;
import org.opends.server.core.WorkQueueStrategy;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

import com.forgerock.opendj.util.AsynchronousFutureResult;

/**
 * Adapter class between LDAP SDK's {@link org.forgerock.opendj.ldap.Connection}
 * and OpenDJ server's
 * {@link org.opends.server.protocols.http.HTTPClientConnection}.
 */
public class SdkConnectionAdapter extends AbstractAsynchronousConnection
{

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /** The HTTP client connection being "adapted". */
  private final HTTPClientConnection clientConnection;

  /** FIXME: do not use constants. */
  private int messageID;

  /** FIXME: do not use constants. */
  private long operationID;

  /** The queueing strategy used for this connection. */
  private QueueingStrategy queueingStrategy = new WorkQueueStrategy();

  /**
   * Whether this connection has been closed by calling {@link #close()} or
   * {@link #close(UnbindRequest, String)}.
   */
  private boolean isClosed;

  /**
   * Constructor.
   *
   * @param clientConnection
   *          the HTTP client connection being "adapted"
   */
  public SdkConnectionAdapter(HTTPClientConnection clientConnection)
  {
    this.clientConnection = clientConnection;
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Void> abandonAsync(AbandonRequest request)
  {
    // TODO Auto-generated method stub
    // for (ConnectionEventListener listener : this.listeners)
    // {
    // listener.
    // }
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> addAsync(AddRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    // AddOperationBasis operation =
    // new AddOperationBasis(clientConnection, operationID, messageID,
    // to(request.getControls()), to(valueOf(request.getName())),
    // to(request.getAllAttributes()));

    // DirectoryServer.enqueueRequest(operation);

    // return StaticUtils.getResponseResult(addOperation);

    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void addConnectionEventListener(ConnectionEventListener listener)
  {
    // not useful so far
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<BindResult> bindAsync(BindRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super BindResult> resultHandler)
  {
    // BindOperationBasis operation =
    // new BindOperationBasis(clientConnection, operationID, messageID,
    // to(request.getControls()), "3", to(request.getName()), "",
    // getCredentials(new byte[] {}));
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void close(UnbindRequest request, String reason)
  {
    isClosed = true;
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<CompareResult> compareAsync(CompareRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super CompareResult> resultHandler)
  {
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> deleteAsync(DeleteRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
      ExtendedRequest<R> request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super R> resultHandler)
  {
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean isClosed()
  {
    return isClosed;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isValid()
  {
    return this.clientConnection.isConnectionValid();
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> modifyAsync(ModifyRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> modifyDNAsync(ModifyDNRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    // TODO Auto-generated method stub
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void removeConnectionEventListener(ConnectionEventListener listener)
  {
    // not useful so far
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> searchAsync(final SearchRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final SearchResultHandler resultHandler)
  {
    // TODO JNR attributes
    LinkedHashSet<String> attributes = null;
    SearchOperationBasis op2 =
        new SearchOperationBasis(clientConnection, operationID, messageID,
            to(request.getControls()), to(valueOf(request.getName())),
            to(request.getScope()), to(request.getDereferenceAliasesPolicy()),
            request.getSizeLimit(), request.getTimeLimit(), request
                .isTypesOnly(), to(request.getFilter()), attributes);

    // TODO JNR set requestID
    final AsynchronousFutureResult<Result, SearchResultHandler> futureResult =
       new AsynchronousFutureResult<Result, SearchResultHandler>(resultHandler);

    try
    {
      clientConnection.addOperationInProgress(op2, futureResult);

      queueingStrategy.enqueueRequest(op2);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      clientConnection.removeOperationInProgress(messageID);
      // TODO JNR add error message??
      futureResult.handleErrorResult(ErrorResultException.newErrorResult(
          ResultCode.OPERATIONS_ERROR, e));
    }
    return futureResult;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return this.clientConnection.toString();
  }
}
