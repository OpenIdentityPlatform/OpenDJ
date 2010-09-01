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

package org.opends.sdk.examples;



import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.opends.sdk.*;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;



/**
 * A simple dummy server that just returns one entry.
 */
public class DummyServer implements
    ServerConnectionFactory<LDAPClientContext, Integer>
{

  private static final class DummyServerConnection implements
      ServerConnection<Integer>
  {
    private final LDAPClientContext clientContext;



    private DummyServerConnection(final LDAPClientContext clientContext)
    {
      this.clientContext = clientContext;
    }



    @Override
    public void handleAbandon(final Integer context,
        final AbandonRequest request) throws UnsupportedOperationException
    {
    }



    @Override
    public void handleAdd(final Integer context, final AddRequest request,
        final ResultHandler<? super Result> handler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
    }



    @Override
    public void handleBind(final Integer context, final int version,
        final BindRequest request,
        final ResultHandler<? super BindResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
      resultHandler.handleResult(Responses.newBindResult(ResultCode.SUCCESS));
    }



    @Override
    public void handleCompare(final Integer context,
        final CompareRequest request,
        final ResultHandler<? super CompareResult> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
    }



    @Override
    public void handleConnectionClosed(final Integer context,
        final UnbindRequest request)
    {
      System.out.println(request);
    }



    @Override
    public void handleConnectionException(final Throwable error)
    {
      System.out.println(error);
    }



    @Override
    public void handleDelete(final Integer context,
        final DeleteRequest request,
        final ResultHandler<? super Result> handler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
    }



    @Override
    public <R extends ExtendedResult> void handleExtendedRequest(
        final Integer context, final ExtendedRequest<R> request,
        final ResultHandler<? super R> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
      if (request.getOID().equals(StartTLSExtendedRequest.OID))
      {
        final R result = request.getResultDecoder().adaptExtendedErrorResult(
            ResultCode.SUCCESS, "", "");
        resultHandler.handleResult(result);
        clientContext.startTLS(sslContext, null, null, false, false);
      }
    }



    @Override
    public void handleModify(final Integer context,
        final ModifyRequest request,
        final ResultHandler<? super Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
    }



    @Override
    public void handleModifyDN(final Integer context,
        final ModifyDNRequest request,
        final ResultHandler<? super Result> resultHandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
    }



    @Override
    public void handleSearch(final Integer context,
        final SearchRequest request,
        final ResultHandler<? super Result> resultHandler,
        final SearchResultHandler searchResulthandler,
        final IntermediateResponseHandler intermediateResponseHandler)
        throws UnsupportedOperationException
    {
      searchResulthandler.handleEntry(ENTRY);
      resultHandler.handleResult(RESULT);
    }
  }



  private static final SearchResultEntry ENTRY = Responses
      .newSearchResultEntry(
          "dn: uid=user.6901,ou=People,dc=example,dc=com",
          "objectClass: person",
          "objectClass: inetorgperson",
          "objectClass: organizationalperson",
          "objectClass: top",
          "cn: Romy Ledet",
          "description: This is the description for Romy Ledet.",
          "employeeNumber: 6901",
          "givenName: Romy",
          "homePhone: ,1 060 737 0385",
          "initials: RRL",
          "l: Hampton Roads",
          "mail: user.6901@example.com",
          "mobile: ,1 101 041 2007",
          "pager: ,1 508 271 7836",
          "postalAddress: Romy Ledet$08019 Dogwood Street$Hampton Roads, MD  55656",
          "postalCode: 55656", "sn: Ledet", "st: MD",
          "street: 08019 Dogwood Street", "telephoneNumber: ,1 361 352 6603",
          "uid: user.6901",
          "userPassword: {SSHA}CIieVowKJtlbNMzaVK8cluycDU20b,YXYYszFA==");
  private static final BindResult RESULT = Responses
      .newBindResult(ResultCode.SUCCESS);

  private static SSLContext sslContext;



  /**
   * Dummy LDAP server implementation.
   *
   * @param args
   *          Command line arguments (ignored).
   * @throws Exception
   *           If an error occurred.
   */
  public static void main(final String[] args) throws Exception
  {
    KeyManagerFactory kmf;
    KeyStore ks;
    final char[] storepass = "newpass".toCharArray();
    final char[] keypass = "wshr.ut".toCharArray();
    final String storename = "newstore";

    sslContext = SSLContext.getInstance("TLS");
    kmf = KeyManagerFactory.getInstance("SunX509");
    final FileInputStream fin = new FileInputStream(storename);
    ks = KeyStore.getInstance("JKS");
    ks.load(fin, storepass);

    kmf.init(ks, keypass);
    sslContext.init(kmf.getKeyManagers(), null, null);
    final LDAPListenerOptions options = new LDAPListenerOptions()
        .setBacklog(4096);

    LDAPListener listener = null;
    try
    {
      listener = new LDAPListener(11389, new DummyServer(), options);
      System.out.println("Press any key to stop the server...");
      System.in.read();
    }
    catch (final IOException e)
    {
      System.out.println("Error listening to port 11389");
      e.printStackTrace();
    }
    finally
    {
      if (listener != null)
      {
        listener.close();
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ServerConnection<Integer> accept(final LDAPClientContext context)
  {
    System.out.println("Connection from: " + context.getPeerAddress());
    return new DummyServerConnection(context);
  }
}
