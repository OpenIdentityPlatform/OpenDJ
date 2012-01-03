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
 *      Portions Copyright 2011-2012 ForgeRock AS
 */

package com.forgerock.opendj.ldap;



import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.*;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.filterchain.DefaultFilterChain;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;

import com.forgerock.opendj.util.CompletedFutureResult;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;



/**
 * LDAP connection implementation.
 * <p>
 * TODO: handle illegal state exceptions.
 */
final class LDAPConnection extends AbstractAsynchronousConnection implements
    Connection
{
  private final org.glassfish.grizzly.Connection<?> connection;
  private Result connectionInvalidReason;
  private FilterChain customFilterChain;
  private boolean isClosed = false;
  private final List<ConnectionEventListener> listeners =
      new CopyOnWriteArrayList<ConnectionEventListener>();
  private final AtomicInteger nextMsgID = new AtomicInteger(1);
  private final AtomicBoolean bindOrStartTLSInProgress = new AtomicBoolean(
      false);
  private final ConcurrentHashMap<Integer, AbstractLDAPFutureResultImpl<?>> pendingRequests =
      new ConcurrentHashMap<Integer, AbstractLDAPFutureResultImpl<?>>();
  private final Object stateLock = new Object();
  private final LDAPWriter ldapWriter = new LDAPWriter();
  private final LDAPOptions options;



  /**
   * Creates a new LDAP connection.
   *
   * @param connection
   *          The Grizzly connection.
   * @param options
   *          The LDAP client options.
   */
  LDAPConnection(final org.glassfish.grizzly.Connection<?> connection,
      final LDAPOptions options)
  {
    this.connection = connection;
    this.options = options;
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Void> abandonAsync(final AbandonRequest request)
  {
    final AbstractLDAPFutureResultImpl<?> pendingRequest;
    final int messageID = nextMsgID.getAndIncrement();

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        return new CompletedFutureResult<Void>(
            newErrorResult(connectionInvalidReason), messageID);
      }
      if (bindOrStartTLSInProgress.get())
      {
        final Result errorResult = Responses.newResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "Bind or Start TLS operation in progress");
        return new CompletedFutureResult<Void>(newErrorResult(errorResult),
            messageID);
      }

      // First remove the future associated with the request to be abandoned.
      pendingRequest = pendingRequests.remove(request.getRequestID());
    }

    if (pendingRequest == null)
    {
      // There has never been a request with the specified message ID or the
      // response has already been received and handled. We can ignore this
      // abandon request.

      // Message ID will be -1 since no request was sent.
      return new CompletedFutureResult<Void>((Void) null);
    }

    pendingRequest.cancel(false);

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.abandonRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
        return new CompletedFutureResult<Void>((Void) null, messageID);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      return new CompletedFutureResult<Void>(newErrorResult(errorResult),
          messageID);
    }
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> addAsync(final AddRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(messageID,
        request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress.get())
      {
        future.setResultOrError(Responses
            .newResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                "Bind or Start TLS operation in progress"));
        return future;
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.addRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public void addConnectionEventListener(final ConnectionEventListener listener)
  {
    Validator.ensureNotNull(listener);
    listeners.add(listener);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<BindResult> bindAsync(final BindRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super BindResult> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();

    BindClient context;
    try
    {
      context = request
          .createBindClient(connection.getPeerAddress() instanceof InetSocketAddress ?
              ((InetSocketAddress) connection
              .getPeerAddress()).getHostName() : connection.getPeerAddress()
              .toString());
    }
    catch (final Exception e)
    {
      // FIXME: I18N need to have a better error message.
      // FIXME: Is this the best result code?
      final Result errorResult = Responses
          .newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR)
          .setDiagnosticMessage(
              "An error occurred while creating a bind context").setCause(e);
      final ErrorResultException error = ErrorResultException
          .newErrorResult(errorResult);
      if (resultHandler != null)
      {
        resultHandler.handleErrorResult(error);
      }
      return new CompletedFutureResult<BindResult>(error, messageID);
    }

    final LDAPBindFutureResultImpl future = new LDAPBindFutureResultImpl(
        messageID, context, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (!pendingRequests.isEmpty())
      {
        future.setResultOrError(Responses.newBindResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "There are other operations pending on this connection"));
        return future;
      }

      if (!bindOrStartTLSInProgress.compareAndSet(false, true))
      {
        future.setResultOrError(Responses.newBindResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "Bind or Start TLS operation in progress"));
        return future;
      }

      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        // Use the bind client to get the initial request instead of using the
        // bind request passed to this method.
        final GenericBindRequest initialRequest = context.nextBindRequest();
        ldapWriter.bindRequest(asn1Writer, messageID, 3, initialRequest);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      bindOrStartTLSInProgress.set(false);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public void close(final UnbindRequest request, final String reason)
  {
    // FIXME: I18N need to internationalize this message.
    Validator.ensureNotNull(request);

    close(
        request,
        false,
        Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED)
            .setDiagnosticMessage(
                "Connection closed by client"
                    + (reason != null ? ": " + reason : "")));
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<CompareResult> compareAsync(final CompareRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super CompareResult> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPCompareFutureResultImpl future = new LDAPCompareFutureResultImpl(
        messageID, request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress.get())
      {
        future.setResultOrError(Responses.newCompareResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "Bind or Start TLS operation in progress"));
        return future;
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.compareRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> deleteAsync(final DeleteRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(messageID,
        request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress.get())
      {
        future.setResultOrError(Responses
            .newResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                "Bind or Start TLS operation in progress"));
        return future;
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.deleteRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public <R extends ExtendedResult> FutureResult<R> extendedRequestAsync(
      final ExtendedRequest<R> request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super R> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPExtendedFutureResultImpl<R> future = new LDAPExtendedFutureResultImpl<R>(
        messageID, request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (request.getOID().equals(StartTLSExtendedRequest.OID))
      {
        if (!pendingRequests.isEmpty())
        {
          future.setResultOrError(request.getResultDecoder()
              .newExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                  "There are pending operations on this connection"));
          return future;
        }
        if (isTLSEnabled())
        {
          future.setResultOrError(request.getResultDecoder()
              .newExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                  "This connection is already TLS enabled"));
          return future;
        }
        if (!bindOrStartTLSInProgress.compareAndSet(false, true))
        {
          future.setResultOrError(request.getResultDecoder()
              .newExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                  "Bind or Start TLS operation in progress"));
          return future;
        }
      }
      else
      {
        if (bindOrStartTLSInProgress.get())
        {
          future.setResultOrError(request.getResultDecoder()
              .newExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                  "Bind or Start TLS operation in progress"));
          return future;
        }
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.extendedRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);
      bindOrStartTLSInProgress.set(false);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isClosed()
  {
    return isClosed;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isValid()
  {
    return connectionInvalidReason == null && !isClosed;
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> modifyAsync(final ModifyRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(messageID,
        request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress.get())
      {
        future.setResultOrError(Responses
            .newResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                "Bind or Start TLS operation in progress"));
        return future;
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.modifyRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> modifyDNAsync(final ModifyDNRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final ResultHandler<? super Result> resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPFutureResultImpl future = new LDAPFutureResultImpl(messageID,
        request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress.get())
      {
        future.setResultOrError(Responses
            .newResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                "Bind or Start TLS operation in progress"));
        return future;
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.modifyDNRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public void removeConnectionEventListener(
      final ConnectionEventListener listener)
  {
    Validator.ensureNotNull(listener);
    listeners.remove(listener);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> searchAsync(final SearchRequest request,
      final IntermediateResponseHandler intermediateResponseHandler,
      final SearchResultHandler resultHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPSearchFutureResultImpl future = new LDAPSearchFutureResultImpl(
        messageID, request, resultHandler, intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress.get())
      {
        future.setResultOrError(Responses
            .newResult(ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
                "Bind or Start TLS operation in progress"));
        return future;
      }
      pendingRequests.put(messageID, future);
    }

    try
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        ldapWriter.searchRequest(asn1Writer, messageID, request);
        connection.write(asn1Writer.getBuffer(), null);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
    catch (final IOException e)
    {
      pendingRequests.remove(messageID);

      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      future.adaptErrorResult(errorResult);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("LDAPConnection(");
    builder.append(connection.getLocalAddress());
    builder.append(',');
    builder.append(connection.getPeerAddress());
    builder.append(')');
    return builder.toString();
  }



  int addPendingRequest(final AbstractLDAPFutureResultImpl<?> request)
      throws ErrorResultException
  {
    final int newMsgID = nextMsgID.getAndIncrement();
    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        throw newErrorResult(connectionInvalidReason);
      }
      pendingRequests.put(newMsgID, request);
    }
    return newMsgID;
  }



  long cancelExpiredRequests(final long currentTime)
  {
    final long timeout = options.getTimeout(TimeUnit.MILLISECONDS);
    long delay = timeout;
    if (timeout > 0)
    {
      for (int requestID : pendingRequests.keySet())
      {
        final AbstractLDAPFutureResultImpl<?> future = pendingRequests
            .get(requestID);
        if (future != null)
        {
          final long diff = (future.getTimestamp() + timeout) - currentTime;
          if (diff <= 0 && pendingRequests.remove(requestID) != null)
          {
            StaticUtils.DEBUG_LOG.fine("Cancelling expired future result: "
                + future);
            final Result result = Responses
                .newResult(ResultCode.CLIENT_SIDE_TIMEOUT);
            future.adaptErrorResult(result);

            abandonAsync(Requests.newAbandonRequest(future.getRequestID()));
          }
          else
          {
            delay = Math.min(delay, diff);
          }
        }
      }
    }
    return delay;
  }



  void close(final UnbindRequest unbindRequest,
      final boolean isDisconnectNotification, final Result reason)
  {
    boolean notifyClose = false;
    boolean notifyErrorOccurred = false;

    synchronized (stateLock)
    {
      if (isClosed)
      {
        // Already closed.
        return;
      }

      if (connectionInvalidReason != null)
      {
        // Already closed.
        isClosed = true;
        return;
      }

      if (unbindRequest != null)
      {
        // User closed.
        isClosed = true;
        notifyClose = true;
      }
      else
      {
        notifyErrorOccurred = true;
      }

      // Mark the connection as invalid.
      if (!isDisconnectNotification)
      {
        // Connection termination was detected locally, so use the provided
        // reason for all subsequent requests.
        connectionInvalidReason = reason;
      }
      else
      {
        // Connection termination was triggered remotely. We don't want to
        // blindly pass on the result code to requests since it could be
        // confused for a genuine response. For example, if the disconnect
        // contained the invalidCredentials result code then this could be
        // misinterpreted as a genuine authentication failure for subsequent
        // bind requests.
        connectionInvalidReason = Responses.newResult(
            ResultCode.CLIENT_SIDE_SERVER_DOWN).setDiagnosticMessage(
            "Connection closed by server");
      }
    }

    // First abort all outstanding requests.
    for (int requestID : pendingRequests.keySet())
    {
      final AbstractLDAPFutureResultImpl<?> future = pendingRequests
          .remove(requestID);
      if (future != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
      }
    }

    // Now try cleanly closing the connection if possible.
    // Only send unbind if specified.
    if (unbindRequest != null && !isDisconnectNotification)
    {
      try
      {
        final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
        try
        {
          ldapWriter.unbindRequest(asn1Writer, nextMsgID.getAndIncrement(),
              unbindRequest);
          connection.write(asn1Writer.getBuffer(), null);
        }
        finally
        {
          asn1Writer.recycle();
        }
      }
      catch (final IOException e)
      {
        // Underlying channel prob blown up. Just ignore.
      }
    }

    try
    {
      connection.close();
    }
    catch (final IOException e)
    {
      // Ignore.
    }

    // Notify listeners.
    if (notifyClose)
    {
      for (final ConnectionEventListener listener : listeners)
      {
        listener.handleConnectionClosed();
      }
    }

    if (notifyErrorOccurred)
    {
      for (final ConnectionEventListener listener : listeners)
      {
        // Use the reason provided in the disconnect notification.
        listener.handleConnectionError(isDisconnectNotification,
            newErrorResult(reason));
      }
    }
  }



  LDAPOptions getLDAPOptions()
  {
    return options;
  }



  AbstractLDAPFutureResultImpl<?> getPendingRequest(final Integer messageID)
  {
    return pendingRequests.get(messageID);
  }



  synchronized void handleUnsolicitedNotification(final ExtendedResult result)
  {
    if (isClosed)
    {
      // Don't notify after connection is closed.
      return;
    }

    for (final ConnectionEventListener listener : listeners)
    {
      listener.handleUnsolicitedNotification(result);
    }
  }



  void installFilter(final Filter filter)
  {
    if (customFilterChain == null)
    {
      customFilterChain = new DefaultFilterChain(
          (FilterChain) connection.getProcessor());
      connection.setProcessor(customFilterChain);
    }

    // Install the SSLFilter in the custom filter chain
    customFilterChain.add(customFilterChain.size() - 1, filter);
  }



  /**
   * Indicates whether or not TLS is enabled on this connection.
   *
   * @return {@code true} if TLS is enabled on this connection, otherwise
   *         {@code false}.
   */
  boolean isTLSEnabled()
  {
    final FilterChain currentFilterChain = (FilterChain) connection
        .getProcessor();
    return currentFilterChain.get(currentFilterChain.size() - 2) instanceof SSLFilter;
  }



  AbstractLDAPFutureResultImpl<?> removePendingRequest(final Integer messageID)
  {
    return pendingRequests.remove(messageID);
  }



  void setBindOrStartTLSInProgress(final boolean state)
  {
    bindOrStartTLSInProgress.set(state);
  }



  synchronized void startTLS(final SSLContext sslContext,
      final List<String> protocols, final List<String> cipherSuites,
      final CompletionHandler<SSLEngine> completionHandler) throws IOException
  {
    if (isTLSEnabled())
    {
      return;
    }

    SSLFilter sslFilter;
    SSLEngineConfigurator sslEngineConfigurator;

    sslEngineConfigurator = new SSLEngineConfigurator(sslContext, true, false,
        false);
    sslEngineConfigurator.setEnabledProtocols(protocols.isEmpty() ? null
        : protocols.toArray(new String[protocols.size()]));
    sslEngineConfigurator.setEnabledCipherSuites(cipherSuites.isEmpty() ? null
        : cipherSuites.toArray(new String[cipherSuites.size()]));
    sslFilter = new SSLFilter(null, sslEngineConfigurator);
    installFilter(sslFilter);
    sslFilter.handshake(connection, completionHandler);
  }



  private void connectionErrorOccurred(final Result reason)
  {
    close(null, false, reason);
  }
}
