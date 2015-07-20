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
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.protocols.http;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletResponse;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AbstractAsynchronousConnection;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.spi.LdapPromiseImpl;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.ResultCode;
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
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AbandonOperationBasis;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.BindOperation;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.core.BoundedWorkQueueStrategy;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.CompareOperationBasis;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ExtendedOperationBasis;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.QueueingStrategy;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.SearchOperationBasis;
import org.opends.server.core.UnbindOperation;
import org.opends.server.core.UnbindOperationBasis;
import org.opends.server.protocols.ldap.AbandonRequestProtocolOp;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Operation;

import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.forgerock.opendj.ldap.ByteString.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.spi.LdapPromiseImpl.*;

/**
 * Adapter class between LDAP SDK's {@link org.forgerock.opendj.ldap.Connection}
 * and OpenDJ server's
 * {@link org.opends.server.protocols.http.HTTPClientConnection}.
 */
public class SdkConnectionAdapter extends AbstractAsynchronousConnection
{

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The HTTP client connection being "adapted". */
  private final HTTPClientConnection clientConnection;

  /**
   * The next message ID (and operation ID) that should be used for this
   * connection.
   */
  private final AtomicInteger nextMessageID = new AtomicInteger(0);

  /** The queueing strategy used for this connection. */
  private final QueueingStrategy queueingStrategy;

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
    this.queueingStrategy =
        new BoundedWorkQueueStrategy(clientConnection.getConnectionHandler()
            .getCurrentConfig().getMaxConcurrentOpsPerConnection());
  }

  private <R> LdapPromise<R> enqueueOperation(Operation operation)
  {
    return enqueueOperation(operation, null);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <R> LdapPromise<R> enqueueOperation(Operation operation, SearchResultHandler entryHandler)
  {
    final LdapPromiseImpl<R> promise = newLdapPromiseImpl(operation.getMessageID());

    try
    {
      operation.setInnerOperation(this.clientConnection.isInnerConnection());

      HTTPConnectionHandler connHandler = this.clientConnection.getConnectionHandler();
      if (connHandler.keepStats())
      {
        connHandler.getStatTracker().updateMessageRead(
            new LDAPMessage(operation.getMessageID(), toRequestProtocolOp(operation)));
      }

      // need this raw cast here to fool the compiler's generic type safety
      // Problem here is due to the generic type R on enqueueOperation()
      clientConnection.addOperationInProgress(operation, (LdapPromiseImpl) promise, entryHandler);
      queueingStrategy.enqueueRequest(operation);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      clientConnection.removeOperationInProgress(operation.getMessageID());
      // TODO JNR add error message??
      promise.handleException(newLdapException(ResultCode.OPERATIONS_ERROR, e));
    }

    return promise;
  }

  private ProtocolOp toRequestProtocolOp(Operation operation)
  {
    if (operation instanceof AbandonOperation)
    {
      final AbandonOperation op = (AbandonOperation) operation;
      return new AbandonRequestProtocolOp(op.getIDToAbandon());
    }
    else if (operation instanceof AddOperation)
    {
      final AddOperation op = (AddOperation) operation;
      return new AddRequestProtocolOp(op.getRawEntryDN(),
          op.getRawAttributes());
    }
    else if (operation instanceof BindOperation)
    {
      final BindOperation op = (BindOperation) operation;
      return new BindRequestProtocolOp(op.getRawBindDN(),
          op.getSASLMechanism(), op.getSASLCredentials());
    }
    else if (operation instanceof CompareOperation)
    {
      final CompareOperation op = (CompareOperation) operation;
      return new CompareRequestProtocolOp(op.getRawEntryDN(), op
          .getRawAttributeType(), op.getAssertionValue());
    }
    else if (operation instanceof DeleteOperation)
    {
      final DeleteOperation op = (DeleteOperation) operation;
      return new DeleteRequestProtocolOp(op.getRawEntryDN());
    }
    else if (operation instanceof ExtendedOperation)
    {
      final ExtendedOperation op = (ExtendedOperation) operation;
      return new ExtendedRequestProtocolOp(op.getRequestOID(), op
          .getRequestValue());
    }
    else if (operation instanceof ModifyDNOperation)
    {
      final ModifyDNOperation op = (ModifyDNOperation) operation;
      return new ModifyDNRequestProtocolOp(op.getRawEntryDN(), op
          .getRawNewRDN(), op.deleteOldRDN(), op.getRawNewSuperior());
    }
    else if (operation instanceof ModifyOperation)
    {
      final ModifyOperation op = (ModifyOperation) operation;
      return new ModifyRequestProtocolOp(op.getRawEntryDN(), op
          .getRawModifications());
    }
    else if (operation instanceof SearchOperation)
    {
      final SearchOperation op = (SearchOperation) operation;
      return new SearchRequestProtocolOp(op.getRawBaseDN(), op.getScope(), op
          .getDerefPolicy(), op.getSizeLimit(), op.getTimeLimit(), op
          .getTypesOnly(), op.getRawFilter(), op.getAttributes());
    }
    else if (operation instanceof UnbindOperation)
    {
      return new UnbindRequestProtocolOp();
    }
    throw new RuntimeException("Not implemented for operation " + operation);
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<Void> abandonAsync(AbandonRequest request)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new AbandonOperationBasis(clientConnection, messageID, messageID,
        to(request.getControls()), request.getRequestID()));
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<Result> addAsync(AddRequest request, IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new AddOperationBasis(clientConnection, messageID, messageID, to(request.getControls()),
        valueOf(request.getName()), to(request.getAllAttributes())));
  }

  /** {@inheritDoc} */
  @Override
  public void addConnectionEventListener(ConnectionEventListener listener)
  {
    // not useful so far
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<BindResult> bindAsync(BindRequest request,
      IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    String userName = request.getName();
    byte[] password = ((SimpleBindRequest) request).getPassword();
    return enqueueOperation(new BindOperationBasis(clientConnection, messageID, messageID, to(request.getControls()),
        "3", ByteString.valueOf(userName), ByteString.wrap(password)));
  }

  /** {@inheritDoc} */
  @Override
  public void close(UnbindRequest request, String reason)
  {
    AuthenticationInfo authInfo = this.clientConnection.getAuthenticationInfo();
    if (authInfo != null && authInfo.isAuthenticated())
    {
      final int messageID = nextMessageID.getAndIncrement();
      final UnbindOperationBasis operation =
          new UnbindOperationBasis(clientConnection, messageID, messageID,
              to(request.getControls()));
      operation.setInnerOperation(this.clientConnection.isInnerConnection());

      // run synchronous
      operation.run();
    }
    else
    {
      this.clientConnection.disconnect(DisconnectReason.UNBIND, false, null);
    }

    // At this point, we try to log the request with OK status code.
    // If it was already logged, it will be a no op.
    this.clientConnection.log(HttpServletResponse.SC_OK);

    isClosed = true;
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<CompareResult> compareAsync(CompareRequest request,
      IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new CompareOperationBasis(clientConnection, messageID, messageID,
        to(request.getControls()), valueOf(request.getName()),
        request.getAttributeDescription().getAttributeType().getOID(),
        request.getAssertionValue()));
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<Result> deleteAsync(DeleteRequest request,
      IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new DeleteOperationBasis(clientConnection, messageID, messageID,
        to(request.getControls()), valueOf(request.getName())));
  }

  /** {@inheritDoc} */
  @Override
  public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request,
      IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new ExtendedOperationBasis(this.clientConnection, messageID, messageID,
        to(request.getControls()), request.getOID(),
        request.getValue()));
  }

  /**
   * Return the queueing strategy used by this connection.
   *
   * @return The queueing strategy used by this connection
   */
  public QueueingStrategy getQueueingStrategy()
  {
    return queueingStrategy;
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
  public LdapPromise<Result> modifyAsync(ModifyRequest request,
      IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new ModifyOperationBasis(clientConnection, messageID, messageID,
        to(request.getControls()), to(request.getName()),
        toModifications(request.getModifications())));
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<Result> modifyDNAsync(ModifyDNRequest request,
      IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new ModifyDNOperationBasis(clientConnection, messageID, messageID,
        to(request.getControls()), to(request.getName()), to(request
            .getNewRDN()), request.isDeleteOldRDN(), to(request
            .getNewSuperior())));
  }

  /** {@inheritDoc} */
  @Override
  public void removeConnectionEventListener(ConnectionEventListener listener)
  {
    // not useful so far
  }

  /** {@inheritDoc} */
  @Override
  public LdapPromise<Result> searchAsync(final SearchRequest request,
      final IntermediateResponseHandler intermediateResponseHandler, final SearchResultHandler entryHandler)
  {
    final int messageID = nextMessageID.getAndIncrement();
    return enqueueOperation(new SearchOperationBasis(clientConnection, messageID, messageID,
        to(request.getControls()), to(request.getName()),
        request.getScope(), request.getDereferenceAliasesPolicy(),
        request.getSizeLimit(), request.getTimeLimit(),
        request.isTypesOnly(), toSearchFilter(request.getFilter()),
        new LinkedHashSet<String>(request.getAttributes())), entryHandler);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return this.clientConnection.toString();
  }
}
