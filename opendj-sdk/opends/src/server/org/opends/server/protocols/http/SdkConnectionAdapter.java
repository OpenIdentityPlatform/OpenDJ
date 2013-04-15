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
import java.util.concurrent.atomic.AtomicInteger;

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
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.opends.server.core.AbandonOperationBasis;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.core.CompareOperationBasis;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.ExtendedOperationBasis;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.QueueingStrategy;
import org.opends.server.core.SearchOperationBasis;
import org.opends.server.core.UnbindOperationBasis;
import org.opends.server.core.WorkQueueStrategy;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Operation;

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

  /**
   * The next message ID (and operation ID) that should be used for this
   * connection.
   */
  private AtomicInteger nextMessageID = new AtomicInteger(0);

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

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <R> FutureResult<R> enqueueOperation(
      Operation operation, ResultHandler<? super R> resultHandler)
  {
    final AsynchronousFutureResult<R, ResultHandler<? super R>> futureResult =
        new AsynchronousFutureResult<R, ResultHandler<? super R>>(
            resultHandler, operation.getMessageID());

    try
    {
      operation.setInnerOperation(this.clientConnection.isInnerConnection());

      // need this raw cast here to fool the compiler's generic type safety
      // Problem here is due to the generic type R on enqueueOperation()
      clientConnection.addOperationInProgress(operation,
          (AsynchronousFutureResult) futureResult);

      queueingStrategy.enqueueRequest(operation);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      clientConnection.removeOperationInProgress(operation.getMessageID());
      // TODO JNR add error message??
      futureResult.handleErrorResult(ErrorResultException.newErrorResult(
          ResultCode.OPERATIONS_ERROR, e));
    }
    return futureResult;
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Void> abandonAsync(AbandonRequest request)
  {
    final int messageID = nextMessageID.getAndIncrement();
    AbandonOperationBasis operation =
        new AbandonOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), request.getRequestID());

    return enqueueOperation(operation, null);
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> addAsync(AddRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    AddOperationBasis operation =
        new AddOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), to(valueOf(request.getName())),
            to(request.getAllAttributes()));

    return enqueueOperation(operation, resultHandler);
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
    final int messageID = nextMessageID.get();
    String userName = request.getName();
    byte[] password = ((SimpleBindRequest) request).getPassword();
    BindOperationBasis operation =
        new BindOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), "3", to(userName), ByteString
                .wrap(password));

    return enqueueOperation(operation, resultHandler);
  }

  /** {@inheritDoc} */
  @Override
  public void close(UnbindRequest request, String reason)
  {
    AuthenticationInfo authInfo = this.clientConnection.getAuthenticationInfo();
    if (authInfo != null && authInfo.isAuthenticated())
    {
      final int messageID = nextMessageID.get();
      UnbindOperationBasis operation =
          new UnbindOperationBasis(clientConnection, messageID, messageID,
              to(request.getControls()));

      // run synchronous
      operation.run();
    }
    else
    {
      this.clientConnection.disconnect(DisconnectReason.UNBIND, false, null);
    }
    isClosed = true;
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<CompareResult> compareAsync(CompareRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super CompareResult> resultHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    CompareOperationBasis operation =
        new CompareOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), to(valueOf(request.getName())),
            request.getAttributeDescription().getAttributeType().getOID(),
            to(request.getAssertionValue()));

    return enqueueOperation(operation, resultHandler);
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> deleteAsync(DeleteRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    DeleteOperationBasis operation =
        new DeleteOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), to(valueOf(request.getName())));

    return enqueueOperation(operation, resultHandler);
  }

  /** {@inheritDoc} */
  @Override
  public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
      ExtendedRequest<R> request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super R> resultHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    ExtendedOperationBasis operation =
        new ExtendedOperationBasis(this.clientConnection, messageID, messageID,
            to(request.getControls()), request.getOID(),
            to(request.getValue()));

    return enqueueOperation(operation, resultHandler);
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
    final int messageID = nextMessageID.getAndIncrement();
    ModifyOperationBasis operation =
        new ModifyOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), to(request.getName()),
            toModifications(request.getModifications()));

    return enqueueOperation(operation, resultHandler);
  }

  /** {@inheritDoc} */
  @Override
  public FutureResult<Result> modifyDNAsync(ModifyDNRequest request,
      IntermediateResponseHandler intermediateResponseHandler,
      ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    ModifyDNOperationBasis operation =
        new ModifyDNOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), to(request.getName()), to(request
                .getNewRDN()), request.isDeleteOldRDN(), to(request
                .getNewSuperior()));

    return enqueueOperation(operation, resultHandler);
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
    final int messageID = nextMessageID.getAndIncrement();
    SearchOperationBasis operation =
        new SearchOperationBasis(clientConnection, messageID, messageID,
            to(request.getControls()), to(valueOf(request.getName())),
            to(request.getScope()), to(request.getDereferenceAliasesPolicy()),
            request.getSizeLimit(), request.getTimeLimit(),
            request.isTypesOnly(), to(request.getFilter()),
            new LinkedHashSet<String>(request.getAttributes()));

    return enqueueOperation(operation, resultHandler);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return this.clientConnection.toString();
  }
}
