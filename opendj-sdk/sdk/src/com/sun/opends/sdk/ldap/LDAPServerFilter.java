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

package com.sun.opends.sdk.ldap;



import static com.sun.opends.sdk.ldap.LDAPConstants.OID_NOTICE_OF_DISCONNECTION;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.*;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.ssl.SSLUtils;
import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * Grizzly filter implementation for decoding LDAP requests and handling server
 * side logic for SSL and SASL operations over LDAP.
 */
final class LDAPServerFilter extends BaseFilter
{
  private abstract class AbstractHandler<R extends Result> implements
      IntermediateResponseHandler, ResultHandler<R>
  {
    protected final int messageID;
    protected final Connection<?> connection;



    protected AbstractHandler(final int messageID,
        final Connection<?> connection)
    {
      this.messageID = messageID;
      this.connection = connection;
    }



    @Override
    public boolean handleIntermediateResponse(
        final IntermediateResponse response)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.intermediateResponse(asn1Writer, messageID, response);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
        return false;
      }
      finally
      {
        asn1Writer.recycle();
      }
      return true;
    }

  }



  private final class AddHandler extends AbstractHandler<Result>
  {
    private AddHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



    @Override
    public void handleResult(final Result result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.addResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class BindHandler extends AbstractHandler<BindResult>
  {
    private BindHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      final Result result = error.getResult();
      if (result instanceof BindResult)
      {
        handleResult((BindResult) result);
      }
      else
      {
        final BindResult newResult = Responses.newBindResult(result
            .getResultCode());
        newResult.setDiagnosticMessage(result.getDiagnosticMessage());
        newResult.setMatchedDN(result.getMatchedDN());
        newResult.setCause(result.getCause());
        for (final Control control : result.getControls())
        {
          newResult.addControl(control);
        }
        handleResult(newResult);
      }
    }



    @Override
    public void handleResult(final BindResult result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.bindResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class ClientContextImpl implements LDAPClientContext
  {
    private final Connection<?> connection;

    // Connection state guarded by stateLock.
    private final Object stateLock = new Object();
    private List<ConnectionEventListener> connectionEventListeners = null;
    private boolean isClosed = false;
    private Throwable connectionError = null;
    private ExtendedResult disconnectNotification = null;

    private ServerConnection<Integer> serverConnection = null;



    private ClientContextImpl(final Connection<?> connection)
    {
      this.connection = connection;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(
        final ConnectionEventListener listener) throws NullPointerException
    {
      Validator.ensureNotNull(listener);

      boolean invokeImmediately = false;
      synchronized (stateLock)
      {
        if (isClosed)
        {
          invokeImmediately = true;
        }
        else
        {
          if (connectionEventListeners == null)
          {
            connectionEventListeners = new LinkedList<ConnectionEventListener>();
          }
          connectionEventListeners.add(listener);
        }
      }

      // Invoke listener immediately if this connection is already closed.
      if (invokeImmediately)
      {
        invokeListener(listener);
      }
    }



    @Override
    public void disconnect()
    {
      LDAPServerFilter.notifyConnectionClosed(connection, -1, null);
    }



    @Override
    public void disconnect(final ResultCode resultCode, final String message)
    {
      Validator.ensureNotNull(resultCode);

      final GenericExtendedResult notification = Responses
          .newGenericExtendedResult(resultCode)
          .setOID(OID_NOTICE_OF_DISCONNECTION).setDiagnosticMessage(message);
      sendUnsolicitedNotification(notification);
      disconnect();
    }



    @Override
    public InetSocketAddress getLocalAddress()
    {
      return (InetSocketAddress) connection.getLocalAddress();
    }



    @Override
    public InetSocketAddress getPeerAddress()
    {
      return (InetSocketAddress) connection.getPeerAddress();
    }



    @Override
    public int getSecurityStrengthFactor()
    {
      int ssf = 0;
      final SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
      if (sslEngine != null)
      {
        final String cipherString = sslEngine.getSession().getCipherSuite();
        for (final Map.Entry<String, Integer> mapEntry : CIPHER_KEY_SIZES
            .entrySet())
        {
          if (cipherString.indexOf(mapEntry.getKey()) >= 0)
          {
            ssf = mapEntry.getValue();
            break;
          }
        }
      }

      return ssf;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed()
    {
      final boolean tmp;
      synchronized (stateLock)
      {
        tmp = isClosed;
      }
      return tmp;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(
        final ConnectionEventListener listener) throws NullPointerException
    {
      Validator.ensureNotNull(listener);

      synchronized (stateLock)
      {
        if (connectionEventListeners != null)
        {
          connectionEventListeners.remove(listener);
        }
      }
    }



    @Override
    public void sendUnsolicitedNotification(final ExtendedResult notification)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.extendedResult(asn1Writer, 0, notification);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        LDAPServerFilter.notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }

      // Update state and notify event listeners if necessary, only if the
      // notification was sent successfully.
      if (notification.getOID().equals(OID_NOTICE_OF_DISCONNECTION))
      {
        // Don't notify listeners yet - wait for disconnect.
        synchronized (stateLock)
        {
          disconnectNotification = notification;
        }
      }
      else
      {
        // Notify listeners.
        List<ConnectionEventListener> tmpList = null;
        synchronized (stateLock)
        {
          if (!isClosed && connectionEventListeners != null)
          {
            tmpList = new ArrayList<ConnectionEventListener>(
                connectionEventListeners);
          }
        }

        if (tmpList != null)
        {
          for (final ConnectionEventListener listener : tmpList)
          {
            listener.handleUnsolicitedNotification(notification);
          }
        }
      }
    }



    @Override
    public void startSASL(final ConnectionSecurityLayer bindContext)
    {
      installFilter(connection, new SASLFilter(bindContext));
    }



    @Override
    public void startTLS(final SSLContext sslContext, final String[] protocols,
        final String[] suites, final boolean wantClientAuth,
        final boolean needClientAuth)
    {
      Validator.ensureNotNull(sslContext);
      SSLEngineConfigurator sslEngineConfigurator;

      sslEngineConfigurator = new SSLEngineConfigurator(sslContext, false,
          false, false);
      sslEngineConfigurator.setEnabledCipherSuites(suites);
      sslEngineConfigurator.setEnabledProtocols(protocols);
      sslEngineConfigurator.setWantClientAuth(wantClientAuth);
      sslEngineConfigurator.setNeedClientAuth(needClientAuth);
      installFilter(connection, new SSLFilter(sslEngineConfigurator, null));
    }



    private ServerConnection<Integer> getServerConnection()
    {
      return serverConnection;
    }



    private void invokeListener(final ConnectionEventListener listener)
    {
      if (connectionError != null)
      {
        final Result result;
        if (connectionError instanceof DecodeException)
        {
          final DecodeException e = (DecodeException) connectionError;
          result = Responses.newResult(ResultCode.PROTOCOL_ERROR)
              .setDiagnosticMessage(e.getMessage()).setCause(connectionError);
        }
        else
        {
          result = Responses.newResult(ResultCode.OTHER)
              .setDiagnosticMessage(connectionError.getMessage())
              .setCause(connectionError);
        }
        listener
            .handleConnectionError(false, ErrorResultException.wrap(result));
      }
      else if (disconnectNotification != null)
      {
        listener.handleConnectionError(true,
            ErrorResultException.wrap(disconnectNotification));
      }
      else
      {
        listener.handleConnectionClosed();
      }
    }



    private void notifyConnectionClosed(final int messageID,
        final UnbindRequest unbindRequest)
    {
      final List<ConnectionEventListener> tmpList;
      synchronized (stateLock)
      {
        if (!isClosed)
        {
          isClosed = true;
        }
        tmpList = connectionEventListeners;
        connectionEventListeners = null;
      }
      if (tmpList != null)
      {
        for (final ConnectionEventListener listener : tmpList)
        {
          invokeListener(listener);
        }
      }
    }



    private void notifyConnectionException(final Throwable error)
    {
      final List<ConnectionEventListener> tmpList;
      synchronized (stateLock)
      {
        if (!isClosed)
        {
          connectionError = error;
          isClosed = true;
        }
        tmpList = connectionEventListeners;
        connectionEventListeners = null;
      }
      if (tmpList != null)
      {
        for (final ConnectionEventListener listener : tmpList)
        {
          invokeListener(listener);
        }
      }
    }



    private void setServerConnection(
        final ServerConnection<Integer> serverConnection)
    {
      this.serverConnection = serverConnection;
    }

  }



  private final class CompareHandler extends AbstractHandler<CompareResult>
  {
    private CompareHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      final Result result = error.getResult();
      if (result instanceof CompareResult)
      {
        handleResult((CompareResult) result);
      }
      else
      {
        final CompareResult newResult = Responses.newCompareResult(result
            .getResultCode());
        newResult.setDiagnosticMessage(result.getDiagnosticMessage());
        newResult.setMatchedDN(result.getMatchedDN());
        newResult.setCause(result.getCause());
        for (final Control control : result.getControls())
        {
          newResult.addControl(control);
        }
        handleResult(newResult);
      }
    }



    @Override
    public void handleResult(final CompareResult result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.compareResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class DeleteHandler extends AbstractHandler<Result>
  {
    private DeleteHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



    @Override
    public void handleResult(final Result result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.deleteResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class ExtendedHandler<R extends ExtendedResult> extends
      AbstractHandler<R>
  {
    private ExtendedHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      final Result result = error.getResult();
      if (result instanceof ExtendedResult)
      {
        handleResult((ExtendedResult) result);
      }
      else
      {
        final ExtendedResult newResult = Responses
            .newGenericExtendedResult(result.getResultCode());
        newResult.setDiagnosticMessage(result.getDiagnosticMessage());
        newResult.setMatchedDN(result.getMatchedDN());
        newResult.setCause(result.getCause());
        for (final Control control : result.getControls())
        {
          newResult.addControl(control);
        }
        handleResult(newResult);
      }
    }



    @Override
    public void handleResult(final ExtendedResult result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.extendedResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class ModifyDNHandler extends AbstractHandler<Result>
  {
    private ModifyDNHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



    @Override
    public void handleResult(final Result result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.modifyDNResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class ModifyHandler extends AbstractHandler<Result>
  {
    private ModifyHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



    @Override
    public void handleResult(final Result result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.modifyResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class SearchHandler extends AbstractHandler<Result> implements
      SearchResultHandler
  {
    private SearchHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



    @Override
    public boolean handleEntry(final SearchResultEntry entry)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.searchResultEntry(asn1Writer, messageID, entry);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
        return false;
      }
      finally
      {
        asn1Writer.recycle();
      }
      return true;
    }



    @Override
    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



    @Override
    public boolean handleReference(final SearchResultReference reference)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.searchResultReference(asn1Writer, messageID, reference);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
        return false;
      }
      finally
      {
        asn1Writer.recycle();
      }
      return true;
    }



    @Override
    public void handleResult(final Result result)
    {
      final ASN1BufferWriter asn1Writer = ASN1BufferWriter.getWriter();
      try
      {
        LDAP_WRITER.searchResult(asn1Writer, messageID, result);
        connection.write(asn1Writer.getBuffer(), null);
      }
      catch (final IOException ioe)
      {
        notifyConnectionException(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  // Map of cipher phrases to effective key size (bits). Taken from the
  // following RFCs: 5289, 4346, 3268,4132 and 4162.
  private static final Map<String, Integer> CIPHER_KEY_SIZES;

  static
  {
    CIPHER_KEY_SIZES = new LinkedHashMap<String, Integer>();
    CIPHER_KEY_SIZES.put("_WITH_AES_256_CBC_", 256);
    CIPHER_KEY_SIZES.put("_WITH_CAMELLIA_256_CBC_", 256);
    CIPHER_KEY_SIZES.put("_WITH_AES_256_GCM_", 256);
    CIPHER_KEY_SIZES.put("_WITH_3DES_EDE_CBC_", 112);
    CIPHER_KEY_SIZES.put("_WITH_AES_128_GCM_", 128);
    CIPHER_KEY_SIZES.put("_WITH_SEED_CBC_", 128);
    CIPHER_KEY_SIZES.put("_WITH_CAMELLIA_128_CBC_", 128);
    CIPHER_KEY_SIZES.put("_WITH_AES_128_CBC_", 128);
    CIPHER_KEY_SIZES.put("_WITH_IDEA_CBC_", 128);
    CIPHER_KEY_SIZES.put("_WITH_DES_CBC_", 56);
    CIPHER_KEY_SIZES.put("_WITH_RC2_CBC_40_", 40);
    CIPHER_KEY_SIZES.put("_WITH_RC4_40_", 40);
    CIPHER_KEY_SIZES.put("_WITH_DES40_CBC_", 40);
    CIPHER_KEY_SIZES.put("_WITH_NULL_", 0);
  }

  private static final LDAPWriter LDAP_WRITER = new LDAPWriter();

  private static final Attribute<ClientContextImpl> LDAP_CONNECTION_ATTR =
    Grizzly.DEFAULT_ATTRIBUTE_BUILDER
      .createAttribute("LDAPServerConnection");

  private static final Attribute<ASN1BufferReader> LDAP_ASN1_READER_ATTR =
    Grizzly.DEFAULT_ATTRIBUTE_BUILDER
      .createAttribute("LDAPASN1Reader");



  private static void notifyConnectionClosed(final Connection<?> connection,
      final int messageID, final UnbindRequest unbindRequest)
  {
    final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR
        .remove(connection);
    if (clientContext != null)
    {
      // First notify connection event listeners.
      clientContext.notifyConnectionClosed(messageID, unbindRequest);

      // Notify the server connection: it may be null if disconnect is invoked
      // during accept.
      final ServerConnection<Integer> serverConnection = clientContext
          .getServerConnection();
      if (serverConnection != null)
      {
        serverConnection.handleConnectionClosed(messageID, unbindRequest);
      }

      // If this close was a result of an unbind request then the connection
      // won't actually be closed yet. To avoid TIME_WAIT TCP state, let the
      // client disconnect.
      if (unbindRequest != null)
      {
        return;
      }

      // Close the connection.
      try
      {
        connection.close();
      }
      catch (final IOException e)
      {
        StaticUtils.DEBUG_LOG.warning("Error closing connection: " + e);
      }
    }
  }



  private static void notifyConnectionException(final Connection<?> connection,
      final Throwable error)
  {
    final ClientContextImpl clientContext = LDAP_CONNECTION_ATTR
        .remove(connection);
    if (clientContext != null)
    {
      // First notify connection event listeners.
      clientContext.notifyConnectionException(error);

      // Notify the server connection: it may be null if disconnect is invoked
      // during accept.
      final ServerConnection<Integer> serverConnection = clientContext
          .getServerConnection();
      if (serverConnection != null)
      {
        serverConnection.handleConnectionException(error);
      }

      // Close the connection.
      try
      {
        connection.close();
      }
      catch (final IOException e)
      {
        StaticUtils.DEBUG_LOG.warning("Error closing connection: " + e);
      }
    }
  }



  private final AbstractLDAPMessageHandler<FilterChainContext> serverRequestHandler =
    new AbstractLDAPMessageHandler<FilterChainContext>()
  {
    @Override
    public void abandonRequest(final FilterChainContext ctx,
        final int messageID, final AbandonRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      conn.handleAbandon(messageID, request);
    }



    @Override
    public void addRequest(final FilterChainContext ctx, final int messageID,
        final AddRequest request) throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final AddHandler handler = new AddHandler(messageID, ctx.getConnection());
      conn.handleAdd(messageID, request, handler, handler);
    }



    @Override
    public void bindRequest(final FilterChainContext ctx, final int messageID,
        final int version, final GenericBindRequest bindContext)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final BindHandler handler = new BindHandler(messageID,
          ctx.getConnection());
      conn.handleBind(messageID, version, bindContext, handler, handler);
    }



    @Override
    public void compareRequest(final FilterChainContext ctx,
        final int messageID, final CompareRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final CompareHandler handler = new CompareHandler(messageID,
          ctx.getConnection());
      conn.handleCompare(messageID, request, handler, handler);
    }



    @Override
    public void deleteRequest(final FilterChainContext ctx,
        final int messageID, final DeleteRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final DeleteHandler handler = new DeleteHandler(messageID,
          ctx.getConnection());
      conn.handleDelete(messageID, request, handler, handler);
    }



    @Override
    public <R extends ExtendedResult> void extendedRequest(
        final FilterChainContext ctx, final int messageID,
        final ExtendedRequest<R> request) throws UnexpectedRequestException
    {
      final ExtendedHandler<R> handler = new ExtendedHandler<R>(messageID,
          ctx.getConnection());

      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      conn.handleExtendedRequest(messageID, request, handler, handler);
    }



    @Override
    public void modifyDNRequest(final FilterChainContext ctx,
        final int messageID, final ModifyDNRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final ModifyDNHandler handler = new ModifyDNHandler(messageID,
          ctx.getConnection());
      conn.handleModifyDN(messageID, request, handler, handler);
    }



    @Override
    public void modifyRequest(final FilterChainContext ctx,
        final int messageID, final ModifyRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final ModifyHandler handler = new ModifyHandler(messageID,
          ctx.getConnection());
      conn.handleModify(messageID, request, handler, handler);
    }



    @Override
    public void searchRequest(final FilterChainContext ctx,
        final int messageID, final SearchRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(
          ctx.getConnection()).getServerConnection();
      final SearchHandler handler = new SearchHandler(messageID,
          ctx.getConnection());
      conn.handleSearch(messageID, request, handler, handler, handler);
    }



    @Override
    public void unbindRequest(final FilterChainContext ctx,
        final int messageID, final UnbindRequest request)
    {
      notifyConnectionClosed(ctx.getConnection(), messageID, request);
    }



    @Override
    public void unrecognizedMessage(final FilterChainContext ctx,
        final int messageID, final byte messageTag,
        final ByteString messageBytes)
    {
      notifyConnectionException(ctx.getConnection(),
          new UnsupportedMessageException(messageID, messageTag, messageBytes));
    }
  };
  private final int maxASN1ElementSize;

  private final LDAPReader ldapReader;

  private final LDAPListenerImpl listener;



  LDAPServerFilter(final LDAPListenerImpl listener,
      final LDAPReader ldapReader, final int maxASN1ElementSize)
  {
    this.listener = listener;
    this.ldapReader = ldapReader;
    this.maxASN1ElementSize = maxASN1ElementSize;
  }



  @Override
  public void exceptionOccurred(final FilterChainContext ctx,
      final Throwable error)
  {
    notifyConnectionException(ctx.getConnection(), error);
  }



  @Override
  public NextAction handleAccept(final FilterChainContext ctx)
      throws IOException
  {
    final Connection<?> connection = ctx.getConnection();
    connection.configureBlocking(true);
    try
    {
      final ClientContextImpl clientContext = new ClientContextImpl(connection);
      final ServerConnection<Integer> serverConn = listener
          .getConnectionFactory().accept(clientContext);
      clientContext.setServerConnection(serverConn);
      LDAP_CONNECTION_ATTR.set(connection, clientContext);
    }
    catch (final ErrorResultException e)
    {
      connection.close();
    }

    return ctx.getStopAction();
  }



  @Override
  public NextAction handleClose(final FilterChainContext ctx)
      throws IOException
  {
    notifyConnectionClosed(ctx.getConnection(), -1, null);
    return ctx.getStopAction();
  }



  @Override
  public NextAction handleRead(final FilterChainContext ctx) throws IOException
  {
    final Buffer buffer = (Buffer) ctx.getMessage();
    ASN1BufferReader asn1Reader = LDAP_ASN1_READER_ATTR
        .get(ctx.getConnection());
    if (asn1Reader == null)
    {
      asn1Reader = new ASN1BufferReader(maxASN1ElementSize);
      LDAP_ASN1_READER_ATTR.set(ctx.getConnection(), asn1Reader);
    }
    asn1Reader.appendBytesRead(buffer);

    try
    {
      while (asn1Reader.elementAvailable())
      {
        ldapReader.decode(asn1Reader, serverRequestHandler, ctx);
      }
    }
    finally
    {
      asn1Reader.disposeBytesRead();
    }

    return ctx.getStopAction();
  }



  private synchronized void installFilter(final Connection<?> connection,
      final com.sun.grizzly.filterchain.Filter filter)
  {
    FilterChain filterChain = (FilterChain) connection.getProcessor();
    if (filter instanceof SSLFilter)
    {
      if (filterChain.get(filterChain.size() - 1) instanceof SSLFilter
          || filterChain.get(filterChain.size() - 2) instanceof SSLFilter)
      {
        // SSLFilter already installed.
        throw new IllegalStateException(
            "SSLFilter already installed on connection");
      }
    }
    if (filter instanceof SASLFilter)
    {
      if (filterChain.get(filterChain.size() - 1) instanceof SASLFilter)
      {
        // SASLFilter already installed.
        throw new IllegalStateException(
            "SASLFilter already installed on connection");
      }
    }

    if (listener.getDefaultFilterChain() == filterChain)
    {
      filterChain = new DefaultFilterChain(filterChain);
      connection.setProcessor(filterChain);
    }

    if (filter instanceof SSLFilter
        && filterChain.get(filterChain.size() - 1) instanceof SASLFilter)
    {
      filterChain.add(filterChain.size() - 2, filter);
    }
    else
    {
      filterChain.add(filterChain.size() - 1, filter);
    }

  }
}
