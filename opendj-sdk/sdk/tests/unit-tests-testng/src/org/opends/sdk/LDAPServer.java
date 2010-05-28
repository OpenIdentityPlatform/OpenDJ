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

package org.opends.sdk;



import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;

import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.opends.sdk.controls.AccountUsabilityRequestControl;
import com.sun.opends.sdk.controls.AccountUsabilityResponseControl;
import com.sun.opends.sdk.ldap.GrizzlyLDAPListenerOptions;



/**
 * A simple ldap server that manages 1000 entries and used for running
 * testcases. //FIXME: make it MT-safe.
 */
public class LDAPServer implements ServerConnection<Integer>,
    ServerConnectionFactory<LDAPClientContext, Integer>
{
  // Creates an abandonable request from the ordinary requests.
  private static class AbandonableRequest implements Request
  {
    // the request.
    private final Request request;

    // whether is has been cancelled.
    private final AtomicBoolean isCanceled;



    // Ctor.
    AbandonableRequest(final Request request)
    {
      this.request = request;
      this.isCanceled = new AtomicBoolean(false);
    }



    public Request addControl(final Control cntrl)
        throws UnsupportedOperationException, NullPointerException
    {
      return request.addControl(cntrl);
    }



    public <C extends Control> C getControl(final ControlDecoder<C> decoder,
        final DecodeOptions options) throws DecodeException,
        NullPointerException
    {
      return request.getControl(decoder, options);
    }



    public List<Control> getControls()
    {
      return request.getControls();
    }



    void cancel()
    {
      isCanceled.set(true);
    }



    boolean isCanceled()
    {
      return isCanceled.get();
    }
  }



  // The singleton instance.
  private static final LDAPServer instance = new LDAPServer();



  /**
   * Returns the singleton instance.
   *
   * @return Singleton instance.
   */
  public static LDAPServer getInstance()
  {
    return instance;
  }



  // The mapping between entry DNs and the corresponding entries.
  private final ConcurrentHashMap<DN, SearchResultEntry> entryMap = new ConcurrentHashMap<DN, SearchResultEntry>();

  // The grizzly transport.
  private final TCPNIOTransport transport = TransportFactory.getInstance()
      .createTCPTransport();

  // The LDAP listener.
  private LDAPListener listener = null;

  // whether the server is running.
  private volatile boolean isRunning;

  // The mapping between the message id and the requests the server is currently
  // handling.
  private final ConcurrentHashMap<Integer, AbandonableRequest> requestsInProgress = new ConcurrentHashMap<Integer, AbandonableRequest>();

  // The Set used for locking dns.
  private final HashSet<DN> lockedDNs = new HashSet<DN>();



  private LDAPServer()
  {
    // Add the root dse first.
    entryMap.put(DN.rootDN(), Responses.newSearchResultEntry(DN.rootDN()));
    for (int i = 0; i < 1000; i++)
    {
      final String dn = String.format("uid=user.%d,ou=people,o=test", i);
      final String cn = String.format("cn: user.%d", i);
      final String sn = String.format("sn: %d", i);
      final String uid = String.format("uid: user.%d", i);

      final DN d = DN.valueOf(dn);
      final SearchResultEntry e = Responses.newSearchResultEntry("dn: " + dn,
          "objectclass: person", "objectclass: inetorgperson",
          "objectclass: top", cn, sn, uid);
      entryMap.put(d, e);
    }
  }



  /**
   * Abandons the request sent by the client.
   *
   * @param context
   * @param request
   * @throws UnsupportedOperationException
   */
  public void abandon(final Integer context, final AbandonRequest request)
      throws UnsupportedOperationException
  {
    // Check if we have any concurrent operation with this message id.
    final AbandonableRequest req = requestsInProgress.get(context);
    if (req == null)
    {
      // Nothing to do here.
      return;
    }
    // Cancel the request
    req.cancel();
    // No response is needed.
  }



  /**
   * @param context
   * @return
   */
  public ServerConnection<Integer> accept(final LDAPClientContext context)
  {
    return this;
  }



  /**
   * Adds the request sent by the client.
   *
   * @param context
   * @param request
   * @param handler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void add(final Integer context, final AddRequest request,
      final ResultHandler<Result> handler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    Result result = null;
    final AbandonableRequest abReq = new AbandonableRequest(request);
    requestsInProgress.put(context, abReq);
    // Get the DN.
    final DN dn = request.getName();
    if (entryMap.containsKey(dn))
    {
      // duplicate entry.
      result = Responses.newResult(ResultCode.ENTRY_ALREADY_EXISTS);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      handler.handleErrorResult(ere);
      // doesn't matter if it was canceled.
      requestsInProgress.remove(context);
      return;
    }

    // Create an entry out of this request.
    final SearchResultEntry entry = Responses.newSearchResultEntry(dn);
    for (final Control control : request.getControls())
    {
      entry.addControl(control);
    }

    for (final Attribute attr : request.getAllAttributes())
    {
      entry.addAttribute(attr);
    }

    if (abReq.isCanceled())
    {
      result = Responses.newResult(ResultCode.CANCELLED);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      handler.handleErrorResult(ere);
      requestsInProgress.remove(context);
      return;
    }
    // Add this to the map.
    entryMap.put(dn, entry);
    requestsInProgress.remove(context);
    result = Responses.newResult(ResultCode.SUCCESS);
    handler.handleResult(result);
  }



  /**
   * @param context
   * @param version
   * @param request
   * @param resultHandler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void bind(final Integer context, final int version,
      final BindRequest request,
      final ResultHandler<? super BindResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    // TODO: all bind types.
    final AbandonableRequest abReq = new AbandonableRequest(request);
    requestsInProgress.put(context, abReq);
    resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
    requestsInProgress.remove(context);
  }



  /**
   * @param context
   * @param request
   */
  public void closed(final Integer context, final UnbindRequest request)
  {

  }



  /**
   * @param error
   */
  public void closed(final Throwable error)
  {

  }



  /**
   * @param context
   * @param request
   * @param resultHandler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void compare(final Integer context, final CompareRequest request,
      final ResultHandler<? super CompareResult> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    CompareResult result = null;
    final AbandonableRequest abReq = new AbandonableRequest(request);
    requestsInProgress.put(context, abReq);
    // Get the DN.
    final DN dn = request.getName();
    if (!entryMap.containsKey(dn))
    {
      // entry not found.
      result = Responses.newCompareResult(ResultCode.NO_SUCH_ATTRIBUTE);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      resultHandler.handleErrorResult(ere);
      // doesn't matter if it was canceled.
      requestsInProgress.remove(context);
      return;
    }

    // Get the entry.
    final SearchResultEntry entry = entryMap.get(dn);
    final AttributeDescription attrDesc = request.getAttributeDescription();
    for (final Attribute attr : entry.getAllAttributes(attrDesc))
    {
      final Iterator<ByteString> it = attr.iterator();
      while (it.hasNext())
      {
        final ByteString s = it.next();
        if (abReq.isCanceled())
        {
          final Result r = Responses.newResult(ResultCode.CANCELLED);
          final ErrorResultException ere = ErrorResultException.wrap(r);
          resultHandler.handleErrorResult(ere);
          requestsInProgress.remove(context);
          return;
        }
        if (s.equals(request.getAssertionValue()))
        {
          result = Responses.newCompareResult(ResultCode.COMPARE_TRUE);
          resultHandler.handleResult(result);
        }
      }
    }
    result = Responses.newCompareResult(ResultCode.COMPARE_FALSE);
    resultHandler.handleResult(result);
    requestsInProgress.remove(context);
  }



  /**
   * @param context
   * @param request
   * @param handler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void delete(final Integer context, final DeleteRequest request,
      final ResultHandler<Result> handler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    Result result = null;
    final AbandonableRequest abReq = new AbandonableRequest(request);
    requestsInProgress.put(context, abReq);
    // Get the DN.
    final DN dn = request.getName();
    if (!entryMap.containsKey(dn))
    {
      // entry is not found.
      result = Responses.newResult(ResultCode.NO_SUCH_OBJECT);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      handler.handleErrorResult(ere);
      // doesn't matter if it was canceled.
      requestsInProgress.remove(context);
      return;
    }

    if (abReq.isCanceled())
    {
      result = Responses.newResult(ResultCode.CANCELLED);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      handler.handleErrorResult(ere);
      requestsInProgress.remove(context);
      return;
    }
    // Remove this from the map.
    entryMap.remove(dn);
    requestsInProgress.remove(context);
  }



  /**
   * @param context
   * @param request
   * @param resultHandler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public <R extends ExtendedResult> void extendedRequest(final Integer context,
      final ExtendedRequest<R> request, final ResultHandler<R> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    // TODO:
  }



  /**
   * Returns whether the server is running or not.
   *
   * @return Whether the server is running.
   */
  public boolean isRunning()
  {
    return isRunning;
  }



  /**
   * @param context
   * @param request
   * @param resultHandler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void modify(final Integer context, final ModifyRequest request,
      final ResultHandler<Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    // TODO:
  }



  /**
   * @param context
   * @param request
   * @param resultHandler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void modifyDN(final Integer context, final ModifyDNRequest request,
      final ResultHandler<Result> resultHandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    // TODO
  }



  /**
   * @param context
   * @param request
   * @param resultHandler
   * @param searchResulthandler
   * @param intermediateResponseHandler
   * @throws UnsupportedOperationException
   */
  public void search(final Integer context, final SearchRequest request,
      final ResultHandler<Result> resultHandler,
      final SearchResultHandler searchResulthandler,
      final IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException
  {
    Result result = null;
    final AbandonableRequest abReq = new AbandonableRequest(request);
    requestsInProgress.put(context, abReq);
    // Get the DN.
    final DN dn = request.getName();
    if (!entryMap.containsKey(dn))
    {
      // Entry not found.
      result = Responses.newResult(ResultCode.NO_SUCH_OBJECT);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      resultHandler.handleErrorResult(ere);
      // Should searchResultHandler handle anything?

      // doesn't matter if it was canceled.
      requestsInProgress.remove(context);
      return;
    }

    if (abReq.isCanceled())
    {
      result = Responses.newResult(ResultCode.CANCELLED);
      final ErrorResultException ere = ErrorResultException.wrap(result);
      resultHandler.handleErrorResult(ere);
      requestsInProgress.remove(context);
      return;
    }

    final SearchResultEntry e = entryMap.get(dn);
    // Check we have had any controls in the request.
    for (final Control control : request.getControls())
    {
      if (control.getOID().equals(AccountUsabilityRequestControl.OID))
      {
        e.addControl(AccountUsabilityResponseControl.newControl(false, false,
            false, 10, false, 0));
      }
    }
    searchResulthandler.handleEntry(e);
    result = Responses.newResult(ResultCode.SUCCESS);
    resultHandler.handleResult(result);
    requestsInProgress.remove(context);
  }



  /**
   * Starts the server.
   *
   * @param port
   * @exception IOException
   */
  public void start(final int port) throws IOException
  {
    if (isRunning)
    {
      return;
    }
    transport.setSelectorRunnersCount(2);
    listener = new LDAPListener(port, new LDAPServer(),
        new GrizzlyLDAPListenerOptions().setTCPNIOTransport(transport)
            .setBacklog(4096));
    transport.start();
    isRunning = true;
  }



  /**
   * Stops the server.
   */
  public void stop()
  {
    if (!isRunning)
    {
      return;
    }
    try
    {
      listener.close();
    }
    catch (final IOException e)
    {
      e.printStackTrace();
    }
    try
    {
      transport.stop();
    }
    catch (final IOException e)
    {
      e.printStackTrace();
    }
    TransportFactory.getInstance().close();
    isRunning = false;
  }
}
