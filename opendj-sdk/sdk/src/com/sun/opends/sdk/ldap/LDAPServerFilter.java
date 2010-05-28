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
import java.util.LinkedHashMap;
import java.util.Map;

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
    protected int messageID;
    protected Connection<?> connection;



    protected AbstractHandler(final int messageID,
        final Connection<?> connection)
    {
      this.messageID = messageID;
      this.connection = connection;
    }



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
        closeConnection(connection, ioe);
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



    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



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
        closeConnection(connection, ioe);
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
        closeConnection(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }
  }



  private final class ClientContextImpl implements LDAPClientContext
  {
    protected Connection<?> connection;



    private ClientContextImpl(final Connection<?> connection)
    {
      this.connection = connection;
    }



    public void disconnect(final boolean sendNotification)
    {
      if (sendNotification)
      {
        final GenericExtendedResult notification = Responses
            .newGenericExtendedResult(ResultCode.SUCCESS);
        notification.setOID(OID_NOTICE_OF_DISCONNECTION);
        sendUnsolicitedNotification(notification);
      }
      closeConnection(connection, -1, null);
    }



    public InetSocketAddress getLocalAddress()
    {
      return (InetSocketAddress) connection.getLocalAddress();
    }



    public InetSocketAddress getPeerAddress()
    {
      return (InetSocketAddress) connection.getPeerAddress();
    }



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
        closeConnection(connection, ioe);
      }
      finally
      {
        asn1Writer.recycle();
      }
    }



    public void startSASL(final ConnectionSecurityLayer bindContext)
    {
      installFilter(connection, new SASLFilter(bindContext));
    }



    public void startTLS(final SSLContext sslContext)
    {
      Validator.ensureNotNull(sslContext);
      SSLEngineConfigurator sslEngineConfigurator;

      sslEngineConfigurator = new SSLEngineConfigurator(sslContext, false,
          false, false);
      installFilter(connection, new SSLFilter(sslEngineConfigurator, null));
    }
  }



  private final class CompareHandler extends AbstractHandler<CompareResult>
  {
    private CompareHandler(final int messageID, final Connection<?> connection)
    {
      super(messageID, connection);
    }



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
        closeConnection(connection, ioe);
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



    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



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
        closeConnection(connection, ioe);
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
        closeConnection(connection, ioe);
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



    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



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
        closeConnection(connection, ioe);
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



    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



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
        closeConnection(connection, ioe);
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
        closeConnection(connection, ioe);
        return false;
      }
      finally
      {
        asn1Writer.recycle();
      }
      return true;
    }



    public void handleErrorResult(final ErrorResultException error)
    {
      handleResult(error.getResult());
    }



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
        closeConnection(connection, ioe);
        return false;
      }
      finally
      {
        asn1Writer.recycle();
      }
      return true;
    }



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
        closeConnection(connection, ioe);
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

  private static final Attribute<ServerConnection<Integer>>
    LDAP_CONNECTION_ATTR = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
      createAttribute("LDAPServerConnection");

  private static final Attribute<ASN1BufferReader> LDAP_ASN1_READER_ATTR =
    Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("LDAPASN1Reader");

  private final AbstractLDAPMessageHandler<FilterChainContext>
    serverRequestHandler = new AbstractLDAPMessageHandler<FilterChainContext>()
  {
    @Override
    public void abandonRequest(final FilterChainContext ctx,
        final int messageID, final AbandonRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      conn.abandon(messageID, request);
    }



    @Override
    public void addRequest(final FilterChainContext ctx, final int messageID,
        final AddRequest request) throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final AddHandler handler = new AddHandler(messageID, ctx.getConnection());
      conn.add(messageID, request, handler, handler);
    }



    @Override
    public void bindRequest(final FilterChainContext ctx, final int messageID,
        final int version, final GenericBindRequest bindContext)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final BindHandler handler = new BindHandler(messageID, ctx
          .getConnection());
      conn.bind(messageID, version, bindContext, handler, handler);
    }



    @Override
    public void compareRequest(final FilterChainContext ctx,
        final int messageID, final CompareRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final CompareHandler handler = new CompareHandler(messageID, ctx
          .getConnection());
      conn.compare(messageID, request, handler, handler);
    }



    @Override
    public void deleteRequest(final FilterChainContext ctx,
        final int messageID, final DeleteRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final DeleteHandler handler = new DeleteHandler(messageID, ctx
          .getConnection());
      conn.delete(messageID, request, handler, handler);
    }



    @Override
    public <R extends ExtendedResult> void extendedRequest(
        final FilterChainContext ctx, final int messageID,
        final ExtendedRequest<R> request) throws UnexpectedRequestException
    {
      final ExtendedHandler<R> handler = new ExtendedHandler<R>(messageID, ctx
          .getConnection());

      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      conn.extendedRequest(messageID, request, handler, handler);
    }



    @Override
    public void modifyDNRequest(final FilterChainContext ctx,
        final int messageID, final ModifyDNRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final ModifyDNHandler handler = new ModifyDNHandler(messageID, ctx
          .getConnection());
      conn.modifyDN(messageID, request, handler, handler);
    }



    @Override
    public void modifyRequest(final FilterChainContext ctx,
        final int messageID, final ModifyRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final ModifyHandler handler = new ModifyHandler(messageID, ctx
          .getConnection());
      conn.modify(messageID, request, handler, handler);
    }



    @Override
    public void searchRequest(final FilterChainContext ctx,
        final int messageID, final SearchRequest request)
        throws UnexpectedRequestException
    {
      final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR.get(ctx
          .getConnection());
      final SearchHandler handler = new SearchHandler(messageID, ctx
          .getConnection());
      conn.search(messageID, request, handler, handler, handler);
    }



    @Override
    public void unbindRequest(final FilterChainContext ctx,
        final int messageID, final UnbindRequest request)
    {
      closeConnection(ctx.getConnection(), messageID, request);
    }



    @Override
    public void unrecognizedMessage(final FilterChainContext ctx,
        final int messageID, final byte messageTag,
        final ByteString messageBytes)
    {
      closeConnection(ctx.getConnection(), new UnsupportedMessageException(
          messageID, messageTag, messageBytes));
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
    closeConnection(ctx.getConnection(), error);
  }



  @Override
  public NextAction handleAccept(final FilterChainContext ctx)
      throws IOException
  {
    final Connection<?> connection = ctx.getConnection();
    connection.configureBlocking(true);
    ServerConnection<Integer> serverConn;
    try
    {
      serverConn = listener.getConnectionFactory().accept(
          new ClientContextImpl(connection));
      LDAP_CONNECTION_ATTR.set(connection, serverConn);
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
    closeConnection(ctx.getConnection(), -1, null);
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



  private void closeConnection(final Connection<?> connection,
      final int messageID, final UnbindRequest unbindRequest)
  {
    final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR
        .remove(connection);
    if (conn != null)
    {
      conn.closed(messageID, unbindRequest);
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



  private void closeConnection(final Connection<?> connection,
      final Throwable error)
  {
    final ServerConnection<Integer> conn = LDAP_CONNECTION_ATTR
        .remove(connection);
    if (conn != null)
    {
      conn.closed(error);
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
