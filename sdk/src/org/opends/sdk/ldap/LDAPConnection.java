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

package org.opends.sdk.ldap;



import static org.opends.sdk.ldap.LDAPConstants.OID_NOTICE_OF_DISCONNECTION;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslException;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;
import org.opends.sdk.extensions.StartTLSRequest;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.sasl.SASLBindRequest;
import org.opends.sdk.sasl.SASLContext;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.Validator;

import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.StreamTransformerFilter;
import com.sun.grizzly.ssl.*;
import com.sun.grizzly.streams.StreamWriter;



/**
 * LDAP connection implementation.
 * <p>
 * TODO: handle illegal state exceptions.
 */
final class LDAPConnection implements AsynchronousConnection
{

  private final class LDAPMessageHandlerImpl extends
      AbstractLDAPMessageHandler
  {

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleAddResult(int messageID, Result result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof ResultFutureImpl<?>)
        {
          ResultFutureImpl<?> future = (ResultFutureImpl<?>) pendingRequest;
          if (future.getRequest() instanceof AddRequest)
          {
            future.handleResult(result);
            return;
          }
        }
        handleIncorrectResponse(pendingRequest);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleBindResult(int messageID, BindResult result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof BindResultFutureImpl<?>)
        {
          BindResultFutureImpl<?> future = ((BindResultFutureImpl<?>) pendingRequest);
          BindRequest request = future.getRequest();

          if (request instanceof SASLBindRequest<?>)
          {
            SASLBindRequest<?> saslBind = (SASLBindRequest<?>) request;
            SASLContext saslContext = future.getSASLContext();

            if ((result.getResultCode() == ResultCode.SUCCESS || result
                .getResultCode() == ResultCode.SASL_BIND_IN_PROGRESS)
                && !saslContext.isComplete())
            {
              try
              {
                saslContext.evaluateCredentials(result
                    .getServerSASLCredentials());
              }
              catch (SaslException e)
              {
                pendingBindOrStartTLS = -1;

                // FIXME: I18N need to have a better error message.
                // FIXME: Is this the best result code?
                Result errorResult = Responses.newResult(
                    ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                    "An error occurred during SASL authentication").setCause(e);
                future.handleErrorResult(errorResult);
                return;
              }
            }

            if (result.getResultCode() == ResultCode.SASL_BIND_IN_PROGRESS)
            {
              // The server is expecting a multi stage bind response.
              messageID = nextMsgID.getAndIncrement();
              ASN1StreamWriter asn1Writer = connFactory
                  .getASN1Writer(streamWriter);

              try
              {
                synchronized (writeLock)
                {
                  pendingRequests.put(messageID, future);
                  try
                  {
                    LDAPEncoder.encodeBindRequest(asn1Writer,
                        messageID, 3, saslBind, saslContext
                            .getSASLCredentials());
                    asn1Writer.flush();
                  }
                  catch (IOException e)
                  {
                    pendingRequests.remove(messageID);

                    // FIXME: what other sort of IOExceptions can be thrown?
                    // FIXME: Is this the best result code?
                    Result errorResult = Responses.newResult(
                        ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
                    connectionErrorOccurred(errorResult);
                    future.handleErrorResult(errorResult);
                  }
                }
              }
              finally
              {
                connFactory.releaseASN1Writer(asn1Writer);
              }
              return;
            }

            if ((result.getResultCode() == ResultCode.SUCCESS)
                && saslContext.isSecure())
            {
              // The connection needs to be secured by the SASL
              // mechanism.
              installFilter(SASLFilter.getInstance(saslContext,
                  connection));
            }
          }
          pendingBindOrStartTLS = -1;
          future.handleResult(result);
        }
        else
        {
          handleIncorrectResponse(pendingRequest);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCompareResult(int messageID, CompareResult result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof CompareResultFutureImpl<?>)
        {
          CompareResultFutureImpl<?> future = (CompareResultFutureImpl<?>) pendingRequest;
          future.handleResult(result);
        }
        else
        {
          handleIncorrectResponse(pendingRequest);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleDeleteResult(int messageID, Result result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof ResultFutureImpl<?>)
        {
          ResultFutureImpl<?> future = (ResultFutureImpl<?>) pendingRequest;
          if (future.getRequest() instanceof DeleteRequest)
          {
            future.handleResult(result);
            return;
          }
        }
        handleIncorrectResponse(pendingRequest);
      }
    }



    /**
     * {@inheritDoc}
     */
    public void handleException(Throwable throwable)
    {
      Result errorResult;
      if(throwable instanceof EOFException)
      {
        // FIXME: Is this the best result code?
        errorResult = Responses.newResult(
            ResultCode.CLIENT_SIDE_SERVER_DOWN).setCause(throwable);
      }
      else
      {
        // FIXME: what other sort of IOExceptions can be thrown?
        // FIXME: Is this the best result code?
        errorResult = Responses.newResult(
            ResultCode.CLIENT_SIDE_DECODING_ERROR).setCause(throwable);
      }
      connectionErrorOccurred(errorResult);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleExtendedResult(int messageID,
        GenericExtendedResult result)
    {
      if (messageID == 0)
      {
        if ((result.getResponseName() != null)
            && result.getResponseName().equals(
                OID_NOTICE_OF_DISCONNECTION))
        {

          Result errorResult = Responses.newResult(result.getResultCode())
              .setDiagnosticMessage(result.getDiagnosticMessage());
          close(null, true, errorResult);
          return;
        }
        else
        {
          // Unsolicited notification received.
          synchronized (writeLock)
          {
            if (isClosed)
            {
              // Don't notify after connection is closed.
              return;
            }

            for (ConnectionEventListener listener : listeners)
            {
              listener
                  .connectionReceivedUnsolicitedNotification(result);
            }
          }
        }
      }

      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);

      if (pendingRequest instanceof ExtendedResultFutureImpl<?, ?>)
      {
        ExtendedResultFutureImpl<?, ?> extendedFuture = ((ExtendedResultFutureImpl<?, ?>) pendingRequest);
        try
        {
          handleExtendedResult0(extendedFuture, result);
        }
        catch (DecodeException de)
        {
          // FIXME: should the connection be closed as well?
          Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_DECODING_ERROR)
              .setDiagnosticMessage(de.getLocalizedMessage()).setCause(
                  de);
          extendedFuture.handleErrorResult(errorResult);
        }
      }
      else
      {
        handleIncorrectResponse(pendingRequest);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleIntermediateResponse(int messageID,
        GenericIntermediateResponse response)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        handleIncorrectResponse(pendingRequest);

        // FIXME: intermediate responses can occur for all operations.

        // if (pendingRequest instanceof ExtendedResultFutureImpl)
        // {
        // ExtendedResultFutureImpl extendedFuture =
        // ((ExtendedResultFutureImpl) pendingRequest);
        // ExtendedRequest request = extendedFuture.getRequest();
        //
        // try
        // {
        // IntermediateResponse decodedResponse =
        // request.getExtendedOperation()
        // .decodeIntermediateResponse(
        // response.getResponseName(),
        // response.getResponseValue());
        // extendedFuture.handleIntermediateResponse(decodedResponse);
        // }
        // catch (DecodeException de)
        // {
        // pendingRequest.failure(de);
        // }
        // }
        // else
        // {
        // handleIncorrectResponse(pendingRequest);
        // }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleModifyDNResult(int messageID, Result result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof ResultFutureImpl<?>)
        {
          ResultFutureImpl<?> future = (ResultFutureImpl<?>) pendingRequest;
          if (future.getRequest() instanceof ModifyDNRequest)
          {
            future.handleResult(result);
            return;
          }
        }
        handleIncorrectResponse(pendingRequest);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleModifyResult(int messageID, Result result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof ResultFutureImpl<?>)
        {
          ResultFutureImpl<?> future = (ResultFutureImpl<?>) pendingRequest;
          if (future.getRequest() instanceof ModifyRequest)
          {
            future.handleResult(result);
            return;
          }
        }
        handleIncorrectResponse(pendingRequest);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSearchResult(int messageID, Result result)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .remove(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof SearchResultFutureImpl<?>)
        {
          ((SearchResultFutureImpl<?>) pendingRequest)
              .handleResult(result);
        }
        else
        {
          handleIncorrectResponse(pendingRequest);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSearchResultEntry(int messageID,
        SearchResultEntry entry)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .get(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof SearchResultFutureImpl<?>)
        {
          ((SearchResultFutureImpl<?>) pendingRequest)
              .handleSearchResultEntry(entry);
        }
        else
        {
          handleIncorrectResponse(pendingRequest);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSearchResultReference(int messageID,
        SearchResultReference reference)
    {
      AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
          .get(messageID);
      if (pendingRequest != null)
      {
        if (pendingRequest instanceof SearchResultFutureImpl<?>)
        {
          ((SearchResultFutureImpl<?>) pendingRequest)
              .handleSearchResultReference(reference);
        }
        else
        {
          handleIncorrectResponse(pendingRequest);
        }
      }
    }



    @Override
    public Control decodeResponseControl(int messageID, String oid,
        boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      ControlDecoder<?> decoder = connFactory.getControlDecoder(oid);
      if (decoder != null)
      {
        return decoder.decode(isCritical, value, schema);
      }
      return super.decodeResponseControl(messageID, oid, isCritical,
          value, schema);
    }



    public ResolvedSchema resolveSchema(String dn)
        throws DecodeException
    {
      DN initialDN;

      try
      {
        initialDN = DN.valueOf(dn, schema);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }

      return new ResolvedSchemaImpl(schema, initialDN);
    }



    public Schema getDefaultSchema()
    {
      return schema;
    }
  }



  private static final class ResolvedSchemaImpl implements
      ResolvedSchema
  {
    private final DN initialDN;

    private final Schema schema;



    private ResolvedSchemaImpl(Schema schema, DN initialDN)
    {
      this.schema = schema;
      this.initialDN = initialDN;
    }



    public AttributeDescription decodeAttributeDescription(
        String attributeDescription) throws DecodeException
    {
      try
      {
        return AttributeDescription.valueOf(attributeDescription,
            schema);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }
    }



    public DN decodeDN(String dn) throws DecodeException
    {
      try
      {
        return DN.valueOf(dn, schema);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }
    }



    public RDN decodeRDN(String rdn) throws DecodeException
    {
      try
      {
        return RDN.valueOf(rdn, schema);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw DecodeException.error(e.getMessageObject());
      }
    }



    public DN getInitialDN()
    {
      return initialDN;
    }



    public Schema getSchema()
    {
      return schema;
    }

  }



  private final Schema schema;

  private final com.sun.grizzly.Connection<?> connection;

  private Result connectionInvalidReason;

  private final LDAPConnectionFactoryImpl connFactory;

  private FilterChain customFilterChain;

  private final LDAPMessageHandler handler = new LDAPMessageHandlerImpl();

  private boolean isClosed = false;

  private final List<ConnectionEventListener> listeners =
      new CopyOnWriteArrayList<ConnectionEventListener>();

  private final AtomicInteger nextMsgID = new AtomicInteger(1);

  private volatile int pendingBindOrStartTLS = -1;

  private final ConcurrentHashMap<Integer, AbstractResultFutureImpl<?, ?>> pendingRequests = new ConcurrentHashMap<Integer, AbstractResultFutureImpl<?, ?>>();

  private final InetSocketAddress serverAddress;

  private StreamWriter streamWriter;

  private final Object writeLock = new Object();



  /**
   * Creates a new LDAP connection.
   *
   * @param connection
   *          The Grizzly connection.
   * @param serverAddress
   *          The address of the server.
   * @param schema
   *          The schema which will be used to decode responses from the
   *          server.
   * @param connFactory
   *          The associated connection factory.
   */
  LDAPConnection(com.sun.grizzly.Connection<?> connection,
      InetSocketAddress serverAddress, Schema schema,
      LDAPConnectionFactoryImpl connFactory)
  {
    this.connection = connection;
    this.serverAddress = serverAddress;
    this.schema = schema;
    this.connFactory = connFactory;
    this.streamWriter = getFilterChainStreamWriter();
  }



  /**
   * {@inheritDoc}
   */
  public void abandon(AbandonRequest request)
  {
    AbstractResultFutureImpl<?, ?> pendingRequest = pendingRequests
        .remove(request.getMessageID());
    if (pendingRequest != null)
    {
      pendingRequest.cancel(false);
      int messageID = nextMsgID.getAndIncrement();
      ASN1StreamWriter asn1Writer = connFactory
          .getASN1Writer(streamWriter);

      try
      {
        synchronized (writeLock)
        {
          if (connectionInvalidReason != null)
          {
            return;
          }
          if (pendingBindOrStartTLS > 0)
          {
            // This is not allowed. We will just ignore this
            // abandon request.
          }
          try
          {
            LDAPEncoder.encodeAbandonRequest(asn1Writer, messageID,
                request);
            asn1Writer.flush();
          }
          catch (IOException e)
          {
            // FIXME: what other sort of IOExceptions can be thrown?
            // FIXME: Is this the best result code?
            Result errorResult = Responses.newResult(
                ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
            connectionErrorOccurred(errorResult);
          }
        }
      }
      finally
      {
        connFactory.releaseASN1Writer(asn1Writer);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Result> add(AddRequest request,
      ResultHandler<Result, P> handler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    ResultFutureImpl<P> future = new ResultFutureImpl<P>(messageID,
        request, handler, p, this, connFactory.getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        pendingRequests.put(messageID, future);
        try
        {
          LDAPEncoder.encodeAddRequest(asn1Writer, messageID, request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public void addConnectionEventListener(
      ConnectionEventListener listener) throws IllegalStateException,
      NullPointerException
  {
    Validator.ensureNotNull(listener);

    synchronized (writeLock)
    {
      if (isClosed)
      {
        throw new IllegalStateException();
      }

      listeners.add(listener);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<BindResult> bind(BindRequest request,
      ResultHandler<? super BindResult, P> handler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    BindResultFutureImpl<P> future = new BindResultFutureImpl<P>(
        messageID, request, handler, p, this, connFactory
            .getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newBindResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        if (!pendingRequests.isEmpty())
        {
          future
              .handleResult(Responses.newBindResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("There are other operations pending on this connection"));
          return future;
        }

        pendingRequests.put(messageID, future);
        pendingBindOrStartTLS = messageID;

        try
        {
          if (request instanceof SASLBindRequest<?>)
          {
            try
            {
              SASLBindRequest<?> saslBind = (SASLBindRequest<?>) request;
              SASLContext saslContext = saslBind
                  .getClientContext(serverAddress.getHostName());
              future.setSASLContext(saslContext);
              LDAPEncoder.encodeBindRequest(asn1Writer, messageID, 3,
                  saslBind, saslContext.getSASLCredentials());
            }
            catch (SaslException e)
            {
              // FIXME: I18N need to have a better error message.
              // FIXME: Is this the best result code?
              Result errorResult = Responses.newResult(
                  ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                  "An error occurred during SASL authentication").setCause(e);
              future.handleErrorResult(errorResult);
              return future;
            }
          }
          else if (request instanceof SimpleBindRequest)
          {
            LDAPEncoder.encodeBindRequest(asn1Writer, messageID, 3,
                (SimpleBindRequest) request);
          }
          else
          {
            pendingRequests.remove(messageID);
            future.handleResult(Responses.newBindResult(
                ResultCode.CLIENT_SIDE_AUTH_UNKNOWN)
                .setDiagnosticMessage("Auth type not supported"));
          }
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public void close()
  {
    close(Requests.newUnbindRequest(), null);
  }


  /**
   * {@inheritDoc}
   */
  public void close(UnbindRequest request, String reason)
      throws NullPointerException {
    // FIXME: I18N need to internationalize this message.
    Validator.ensureNotNull(request);

    close(request, false,
        Responses.newResult(ResultCode.CLIENT_SIDE_USER_CANCELLED)
            .setDiagnosticMessage("Connection closed by client" +
            (reason != null ? ": " + reason : "")));
  }


  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<CompareResult> compare(
      CompareRequest request,
      ResultHandler<? super CompareResult, P> handler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    CompareResultFutureImpl<P> future = new CompareResultFutureImpl<P>(
        messageID, request, handler, p, this, connFactory
            .getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newCompareResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        pendingRequests.put(messageID, future);
        try
        {
          LDAPEncoder.encodeCompareRequest(asn1Writer, messageID,
              request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Result> delete(DeleteRequest request,
      ResultHandler<Result, P> handler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    ResultFutureImpl<P> future = new ResultFutureImpl<P>(messageID,
        request, handler, p, this, connFactory.getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        pendingRequests.put(messageID, future);
        try
        {
          LDAPEncoder.encodeDeleteRequest(asn1Writer, messageID,
              request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public <R extends Result, P> ResultFuture<R> extendedRequest(
      ExtendedRequest<R> request, ResultHandler<? super R, P> handler,
      P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    ExtendedResultFutureImpl<R, P> future = new ExtendedResultFutureImpl<R, P>(
        messageID, request, handler, p, this, connFactory
            .getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future.handleResult(request.getExtendedOperation()
              .decodeResponse(ResultCode.OPERATIONS_ERROR, "",
                  "Bind or Start TLS operation in progress"));
          return future;
        }
        if (request.getRequestName().equals(
            StartTLSRequest.OID_START_TLS_REQUEST))
        {
          if (!pendingRequests.isEmpty())
          {
            future.handleResult(request.getExtendedOperation()
                .decodeResponse(ResultCode.OPERATIONS_ERROR, "",
                    "There are pending operations on this connection"));
            return future;
          }
          if (isTLSEnabled())
          {
            future.handleResult(request.getExtendedOperation()
                .decodeResponse(ResultCode.OPERATIONS_ERROR, "",
                    "This connection is already TLS enabled"));
          }
          pendingBindOrStartTLS = messageID;
        }
        pendingRequests.put(messageID, future);

        try
        {
          LDAPEncoder.encodeExtendedRequest(asn1Writer, messageID,
              request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Result> modify(ModifyRequest request,
      ResultHandler<Result, P> handler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    ResultFutureImpl<P> future = new ResultFutureImpl<P>(messageID,
        request, handler, p, this, connFactory.getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        pendingRequests.put(messageID, future);
        try
        {
          LDAPEncoder.encodeModifyRequest(asn1Writer, messageID,
              request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Result> modifyDN(ModifyDNRequest request,
      ResultHandler<Result, P> handler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    ResultFutureImpl<P> future = new ResultFutureImpl<P>(messageID,
        request, handler, p, this, connFactory.getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        pendingRequests.put(messageID, future);
        try
        {
          LDAPEncoder.encodeModifyDNRequest(asn1Writer, messageID,
              request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * {@inheritDoc}
   */
  public void removeConnectionEventListener(
      ConnectionEventListener listener) throws NullPointerException
  {
    Validator.ensureNotNull(listener);

    synchronized (writeLock)
    {
      listeners.remove(listener);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <P> ResultFuture<Result> search(SearchRequest request,
      ResultHandler<Result, P> resultHandler,
      SearchResultHandler<P> searchResulthandler, P p)
  {
    int messageID = nextMsgID.getAndIncrement();
    SearchResultFutureImpl<P> future = new SearchResultFutureImpl<P>(
        messageID, request, resultHandler, searchResulthandler, p,
        this, connFactory.getHandlerInvokers());
    ASN1StreamWriter asn1Writer = connFactory
        .getASN1Writer(streamWriter);

    try
    {
      synchronized (writeLock)
      {
        if (connectionInvalidReason != null)
        {
          future.handleErrorResult(connectionInvalidReason);
          return future;
        }
        if (pendingBindOrStartTLS > 0)
        {
          future
              .handleResult(Responses.newResult(ResultCode.OPERATIONS_ERROR)
                  .setDiagnosticMessage("Bind or Start TLS operation in progress"));
          return future;
        }
        pendingRequests.put(messageID, future);
        try
        {
          LDAPEncoder.encodeSearchRequest(asn1Writer, messageID,
              request);
          asn1Writer.flush();
        }
        catch (IOException e)
        {
          pendingRequests.remove(messageID);

          // FIXME: what other sort of IOExceptions can be thrown?
          // FIXME: Is this the best result code?
          Result errorResult = Responses.newResult(
              ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
          connectionErrorOccurred(errorResult);
          future.handleErrorResult(errorResult);
        }
      }
    }
    finally
    {
      connFactory.releaseASN1Writer(asn1Writer);
    }

    return future;
  }



  /**
   * Returns the LDAP message handler associated with this connection.
   *
   * @return The LDAP message handler associated with this connection.
   */
  LDAPMessageHandler getLDAPMessageHandler()
  {
    return handler;
  }



  /**
   * Indicates whether or not TLS is enabled on this connection.
   *
   * @return {@code true} if TLS is enabled on this connection,
   *         otherwise {@code false}.
   */
  boolean isTLSEnabled()
  {
    FilterChain currentFilterChain = (FilterChain) connection
        .getProcessor();
    return currentFilterChain.get(2) instanceof SSLFilter;
  }



  private void close(UnbindRequest unbindRequest,
      boolean isDisconnectNotification, Result reason)
  {
    synchronized (writeLock)
    {
      boolean notifyClose = false;
      boolean notifyErrorOccurred = false;

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
        if (notifyClose)
        {
          // TODO: uncomment if close notification is required.
          // for (ConnectionEventListener listener : listeners)
          // {
          // listener.connectionClosed(this);
          // }
        }
        return;
      }

      // First abort all outstanding requests.
      for (AbstractResultFutureImpl<?, ?> future : pendingRequests
          .values())
      {
        if (pendingBindOrStartTLS <= 0)
        {
          ASN1StreamWriter asn1Writer = connFactory
              .getASN1Writer(streamWriter);
          int messageID = nextMsgID.getAndIncrement();
          AbandonRequest abandon = Requests.newAbandonRequest(future
              .getMessageID());
          try
          {
            LDAPEncoder.encodeAbandonRequest(asn1Writer, messageID,
                abandon);
            asn1Writer.flush();
          }
          catch (IOException e)
          {
            // Underlying channel probably blown up. Just ignore.
          }
          finally
          {
            connFactory.releaseASN1Writer(asn1Writer);
          }
        }

        future.handleErrorResult(reason);
      }
      pendingRequests.clear();

      // Now try cleanly closing the connection if possible.
      try
      {
        ASN1StreamWriter asn1Writer = connFactory
            .getASN1Writer(streamWriter);
        if (unbindRequest == null)
        {
          unbindRequest = Requests.newUnbindRequest();
        }

        try
        {
          LDAPEncoder.encodeUnbindRequest(asn1Writer, nextMsgID
              .getAndIncrement(), unbindRequest);
          asn1Writer.flush();
        }
        finally
        {
          connFactory.releaseASN1Writer(asn1Writer);
        }
      }
      catch (IOException e)
      {
        // Underlying channel prob blown up. Just ignore.
      }

      try
      {
        streamWriter.close();
      }
      catch (IOException e)
      {
        // Ignore.
      }

      try
      {
        connection.close();
      }
      catch (IOException e)
      {
        // Ignore.
      }

      // Mark the connection as invalid.
      connectionInvalidReason = reason;

      // Notify listeners.
      if (notifyClose)
      {
        // TODO: uncomment if close notification is required.
        // for (ConnectionEventListener listener : listeners)
        // {
        // listener.connectionClosed(this);
        // }
      }

      if (notifyErrorOccurred)
      {
        for (ConnectionEventListener listener : listeners)
        {
          listener.connectionErrorOccurred(isDisconnectNotification,
              ErrorResultException.wrap(reason));
        }
      }
    }
  }



  private void connectionErrorOccurred(Result reason)
  {
    close(null, false, reason);
  }



  // TODO uncomment if we decide these methods are useful.
  /**
   * {@inheritDoc}
   */
  public boolean isClosed()
  {
    synchronized (writeLock)
    {
      return isClosed;
    }
  }
  //
  //
  //
  // /**
  // * {@inheritDoc}
  // */
  // public boolean isValid() throws InterruptedException
  // {
  // synchronized (writeLock)
  // {
  // return connectionInvalidReason == null;
  // }
  // }
  //
  //
  //
  // /**
  // * {@inheritDoc}
  // */
  // public boolean isValid(long timeout, TimeUnit unit)
  // throws InterruptedException, TimeoutException
  // {
  // // FIXME: no support for timeout.
  // return isValid();
  // }

  private StreamWriter getFilterChainStreamWriter()
  {
    StreamWriter writer = connection.getStreamWriter();
    FilterChain currentFilterChain = (FilterChain) connection
        .getProcessor();
    for (Filter filter : currentFilterChain)
    {
      if (filter instanceof StreamTransformerFilter)
      {
        writer = ((StreamTransformerFilter) filter)
            .getStreamWriter(writer);
      }
    }

    return writer;
  }



  // Needed in order to expose type information.
  private <R extends Result> void handleExtendedResult0(
      ExtendedResultFutureImpl<R, ?> future,
      GenericExtendedResult result) throws DecodeException
  {
    R decodedResponse = future.decodeResponse(result.getResultCode(),
        result.getMatchedDN(), result.getDiagnosticMessage(), result
            .getResponseName(), result.getResponseValue());

    if (future.getRequest() instanceof StartTLSRequest)
    {
      if (result.getResultCode() == ResultCode.SUCCESS)
      {
        StartTLSRequest request = (StartTLSRequest) future.getRequest();
        try
        {
          startTLS(request.getSSLContext());
        }
        catch (ErrorResultException e)
        {
          future.handleErrorResult(e.getResult());
          return;
        }
      }
      pendingBindOrStartTLS = -1;
    }

    future.handleResult(decodedResponse);
  }



  private void handleIncorrectResponse(
      AbstractResultFutureImpl<?, ?> pendingRequest)
  {
    // FIXME: I18N need to have a better error message.
    Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_DECODING_ERROR)
        .setDiagnosticMessage("LDAP response message did not match request");

    pendingRequest.handleErrorResult(errorResult);
    connectionErrorOccurred(errorResult);
  }



  private void startTLS(SSLContext sslContext)
      throws ErrorResultException
  {
    SSLHandshaker sslHandshaker = connFactory.getSslHandshaker();
    SSLFilter sslFilter;
    SSLEngineConfigurator sslEngineConfigurator;
    if (sslContext == connFactory.getSSLContext())
    {
      // Use factory SSL objects since it is the same SSLContext
      sslFilter = connFactory.getSSlFilter();
      sslEngineConfigurator = connFactory.getSSlEngineConfigurator();
    }
    else
    {
      sslEngineConfigurator = new SSLEngineConfigurator(sslContext,
          true, false, false);
      sslFilter = new SSLFilter(sslEngineConfigurator, sslHandshaker);
    }
    installFilter(sslFilter);

    performSSLHandshake(sslHandshaker, sslEngineConfigurator);
  }



  void performSSLHandshake(SSLHandshaker sslHandshaker,
      SSLEngineConfigurator sslEngineConfigurator)
      throws ErrorResultException
  {
    SSLStreamReader reader = new SSLStreamReader(connection
        .getStreamReader());
    SSLStreamWriter writer = new SSLStreamWriter(connection
        .getStreamWriter());

    try
    {
      sslHandshaker.handshake(reader, writer, sslEngineConfigurator)
          .get();
    }
    catch (ExecutionException ee)
    {
      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_CONNECT_ERROR).setCause(ee.getCause());
      connectionErrorOccurred(errorResult);
      throw ErrorResultException.wrap(errorResult);
    }
    catch (Exception e)
    {
      // FIXME: what other sort of IOExceptions can be thrown?
      // FIXME: Is this the best result code?
      Result errorResult = Responses.newResult(
          ResultCode.CLIENT_SIDE_CONNECT_ERROR).setCause(e);
      connectionErrorOccurred(errorResult);
      throw ErrorResultException.wrap(errorResult);
    }
  }



  synchronized void installFilter(Filter filter)
  {
    if (customFilterChain == null)
    {
      customFilterChain = connFactory.getDefaultFilterChainFactory()
          .create();
      connection.setProcessor(customFilterChain);
    }

    // Install the SSLFilter in the custom filter chain
    Filter oldFilter = customFilterChain.remove(customFilterChain
        .size() - 1);
    customFilterChain.add(filter);
    customFilterChain.add(oldFilter);

    // Update stream writer
    streamWriter = getFilterChainStreamWriter();
  }
}
