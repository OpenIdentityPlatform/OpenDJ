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

package com.sun.opends.sdk.ldap;



import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.opends.sdk.*;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.filterchain.DefaultFilterChain;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.opends.sdk.util.CompletedFutureResult;
import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * LDAP connection implementation.
 * <p>
 * TODO: handle illegal state exceptions.
 */
final class LDAPConnection extends AbstractAsynchronousConnection implements
    AsynchronousConnection
{
  private final com.sun.grizzly.Connection<?> connection;

  private Result connectionInvalidReason;

  private FilterChain customFilterChain;

  private boolean isClosed = false;

  private final List<ConnectionEventListener> listeners =
    new CopyOnWriteArrayList<ConnectionEventListener>();

  private final AtomicInteger nextMsgID = new AtomicInteger(1);

  private boolean bindOrStartTLSInProgress = false;

  private final HashMap<Integer, AbstractLDAPFutureResultImpl<?>>
    pendingRequests = new HashMap<Integer, AbstractLDAPFutureResultImpl<?>>();

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
  LDAPConnection(final com.sun.grizzly.Connection<?> connection,
      final LDAPOptions options)
  {
    this.connection = connection;
    this.options = options;
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Void> abandon(final AbandonRequest request)
  {
    // First remove the future associated with the request to be abandoned.
    final AbstractLDAPFutureResultImpl<?> pendingRequest = pendingRequests
        .remove(request.getMessageID());

    if (pendingRequest == null)
    {
      // There has never been a request with the specified message ID or the
      // response has already been received and handled. We can ignore this
      // abandon request.

      // Message ID will be -1 since no request was sent.
      return new CompletedFutureResult<Void>((Void) null);
    }

    pendingRequest.cancel(false);
    final int messageID = nextMsgID.getAndIncrement();

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        return new CompletedFutureResult<Void>(ErrorResultException
            .wrap(connectionInvalidReason), messageID);
      }
      if (bindOrStartTLSInProgress)
      {
        final Result errorResult = Responses.newResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "Bind or Start TLS operation in progress");
        return new CompletedFutureResult<Void>(ErrorResultException
            .wrap(errorResult), messageID);
      }
    }

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
      return new CompletedFutureResult<Void>(ErrorResultException
          .wrap(errorResult), messageID);
    }
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> add(final AddRequest request,
      final ResultHandler<Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
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
      if (bindOrStartTLSInProgress)
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
      throws IllegalStateException, NullPointerException
  {
    Validator.ensureNotNull(listener);
    listeners.add(listener);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<BindResult> bind(final BindRequest request,
      final ResultHandler<? super BindResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();

    BindClient context;
    try
    {
      context = request
          .createBindClient(connection.getPeerAddress().toString());
    }
    catch (final Exception e)
    {
      // FIXME: I18N need to have a better error message.
      // FIXME: Is this the best result code?
      final Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
          "An error occurred while creating a bind context").setCause(e);
      final ErrorResultException error = ErrorResultException.wrap(errorResult);
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
      if (bindOrStartTLSInProgress)
      {
        future.setResultOrError(Responses.newBindResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "Bind or Start TLS operation in progress"));
        return future;
      }
      if (!pendingRequests.isEmpty())
      {
        future.setResultOrError(Responses.newBindResult(
            ResultCode.OPERATIONS_ERROR).setDiagnosticMessage(
            "There are other operations pending on this connection"));
        return future;
      }

      pendingRequests.put(messageID, future);
      bindOrStartTLSInProgress = true;
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
      throws NullPointerException
  {
    // FIXME: I18N need to internationalize this message.
    Validator.ensureNotNull(request);

    close(request, false, Responses.newResult(
        ResultCode.CLIENT_SIDE_USER_CANCELLED).setDiagnosticMessage(
        "Connection closed by client" + (reason != null ? ": " + reason : "")));
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<CompareResult> compare(final CompareRequest request,
      final ResultHandler<? super CompareResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
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
      if (bindOrStartTLSInProgress)
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
  public FutureResult<Result> delete(final DeleteRequest request,
      final ResultHandler<Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
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
      if (bindOrStartTLSInProgress)
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
  public <R extends ExtendedResult> FutureResult<R> extendedRequest(
      final ExtendedRequest<R> request,
      final ResultHandler<? super R> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
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
      if (bindOrStartTLSInProgress)
      {
        future.setResultOrError(request.getResultDecoder()
            .adaptExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                "Bind or Start TLS operation in progress"));
        return future;
      }
      if (request.getOID().equals(StartTLSExtendedRequest.OID))
      {
        if (!pendingRequests.isEmpty())
        {
          future.setResultOrError(request.getResultDecoder()
              .adaptExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                  "There are pending operations on this connection"));
          return future;
        }
        if (isTLSEnabled())
        {
          future.setResultOrError(request.getResultDecoder()
              .adaptExtendedErrorResult(ResultCode.OPERATIONS_ERROR, "",
                  "This connection is already TLS enabled"));
        }
        bindOrStartTLSInProgress = true;
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
  public FutureResult<Result> modify(final ModifyRequest request,
      final ResultHandler<Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
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
      if (bindOrStartTLSInProgress)
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
  public FutureResult<Result> modifyDN(final ModifyDNRequest request,
      final ResultHandler<Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
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
      if (bindOrStartTLSInProgress)
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
      final ConnectionEventListener listener) throws NullPointerException
  {
    Validator.ensureNotNull(listener);
    listeners.remove(listener);
  }



  /**
   * {@inheritDoc}
   */
  public FutureResult<Result> search(final SearchRequest request,
      final ResultHandler<Result> resultHandler,
      final SearchResultHandler searchResultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
  {
    final int messageID = nextMsgID.getAndIncrement();
    final LDAPSearchFutureResultImpl future = new LDAPSearchFutureResultImpl(
        messageID, request, resultHandler, searchResultHandler,
        intermediateResponseHandler, this);

    synchronized (stateLock)
    {
      if (connectionInvalidReason != null)
      {
        future.adaptErrorResult(connectionInvalidReason);
        return future;
      }
      if (bindOrStartTLSInProgress)
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



  int addPendingRequest(final AbstractLDAPFutureResultImpl<?> request)
  {
    final int newMsgID = nextMsgID.getAndIncrement();
    pendingRequests.put(newMsgID, request);
    return newMsgID;
  }



  long cancelExpiredRequests(final long currentTime)
  {
    final long timeout = options.getTimeout(TimeUnit.MILLISECONDS);
    long delay = timeout;
    if (timeout > 0)
    {
      synchronized (stateLock)
      {
        for (final Iterator<AbstractLDAPFutureResultImpl<?>> i = pendingRequests
            .values().iterator(); i.hasNext();)
        {
          final AbstractLDAPFutureResultImpl<?> future = i.next();
          final long diff = (future.getTimestamp() + timeout) - currentTime;
          if (diff <= 0)
          {
            StaticUtils.DEBUG_LOG.fine("Cancelling expired future result: "
                + future);
            final Result result = Responses
                .newResult(ResultCode.CLIENT_SIDE_TIMEOUT);
            future.adaptErrorResult(result);
            i.remove();

            abandon(Requests.newAbandonRequest(future.getRequestID()));
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

      if (connectionInvalidReason != null)
      {
        // Already invalid.
        return;
      }

      // Mark the connection as invalid.
      connectionInvalidReason = reason;
    }

    // First abort all outstanding requests.
    for (final AbstractLDAPFutureResultImpl<?> future : pendingRequests
        .values())
    {
      if (!bindOrStartTLSInProgress)
      {
        final int messageID = nextMsgID.getAndIncrement();
        final AbandonRequest abandon = Requests.newAbandonRequest(future
            .getRequestID());
        try
        {
          final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
          try
          {
            ldapWriter.abandonRequest(asn1Writer, messageID, abandon);
            connection.write(asn1Writer.getBuffer(), null);
          }
          finally
          {
            asn1Writer.recycle();
          }
        }
        catch (final IOException e)
        {
          // Underlying channel probably blown up. Just ignore.
        }
      }

      future.adaptErrorResult(reason);
    }
    pendingRequests.clear();

    // Now try cleanly closing the connection if possible.
    // Only send unbind if specified.
    if (unbindRequest != null)
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
        listener.connectionClosed();
      }
    }

    if (notifyErrorOccurred)
    {
      for (final ConnectionEventListener listener : listeners)
      {
        listener.connectionErrorOccurred(isDisconnectNotification,
            ErrorResultException.wrap(reason));
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
      listener.connectionReceivedUnsolicitedNotification(result);
    }
  }



  void installFilter(final Filter filter)
  {
    if (customFilterChain == null)
    {
      customFilterChain = new DefaultFilterChain((FilterChain) connection
          .getProcessor());
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
    bindOrStartTLSInProgress = state;
  }



  synchronized void startTLS(final SSLContext sslContext,
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
    sslFilter = new SSLFilter(null, sslEngineConfigurator);
    installFilter(sslFilter);
    sslFilter.handshake(connection, completionHandler);
  }



  private void connectionErrorOccurred(final Result reason)
  {
    close(null, false, reason);
  }
}
