/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.opends.admin.ads.util.BlindTrustManager;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.LDAPException;

/** Modeled like an SDK Connection, but implemented using the servers' ProtocolOp classes */
@SuppressWarnings("javadoc")
public final class RemoteConnection implements Closeable
{
  private final String host;
  private final Socket socket;
  private LDAPReader r;
  private LDAPWriter w;
  private AtomicInteger messageID = new AtomicInteger(1);

  public RemoteConnection(String host, int port) throws Exception
  {
    this(host, port, false);
  }

  public RemoteConnection(String host, int port, boolean secure) throws Exception
  {
    this.host = host;
    socket = secure ? getSslSocket(host, port) : new Socket(host, port);
    r = new LDAPReader(socket);
    w = new LDAPWriter(socket);
    TestCaseUtils.configureSocket(socket);
  }

  private Socket getSslSocket(String host, int port) throws Exception
  {
    SSLContext sslCtx = SSLContext.getInstance("TLSv1");
    TrustManager[] tm = new TrustManager[] { new BlindTrustManager() };
    sslCtx.init(null, tm, new SecureRandom());
    SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
    return socketFactory.createSocket(host, port);
  }

  public LDAPMessage bind(SimpleBindRequest bindRequest) throws IOException, LDAPException
  {
    return bind(bindRequest, true);
  }

  public LDAPMessage bind(SimpleBindRequest bindRequest, boolean throwOnExceptionalResultCode)
      throws IOException, LDAPException
  {
    return bind(bindRequest.getName(), bindRequest.getPassword(),
        throwOnExceptionalResultCode, bindRequest.getControls());
  }

  public void bind(String bindDN, String bindPassword, Control... controls)
      throws IOException, LDAPException
  {
    bind(bindDN, bindPassword.getBytes(), true, Arrays.asList(controls));
  }

  private LDAPMessage bind(String bindDN, byte[] bindPassword, boolean throwOnExceptionalResultCode,
      List<Control> controls) throws IOException, LDAPException
  {
    writeMessage(new BindRequestProtocolOp(bs(bindDN), 3, bs(bindPassword)), to(controls));
    LDAPMessage message = readMessage();
    if (throwOnExceptionalResultCode)
    {
      BindResponseProtocolOp response = message.getBindResponseProtocolOp();
      validateNoException(response.getResultCode(), response.getErrorMessage());
      return message;
    }
    return message;
  }

  public void unbind() throws IOException, LDAPException
  {
    writeMessage(new UnbindRequestProtocolOp());
  }

  public LDAPMessage add(AddRequest addRequest) throws IOException, LDAPException
  {
    return add(addRequest, true);
  }

  public LDAPMessage add(AddRequest addRequest, boolean throwOnExceptionalResultCode)
      throws IOException, LDAPException
  {
    writeMessage(addProtocolOp(addRequest), to(addRequest.getControls()));
    LDAPMessage message = readMessage();
    if (throwOnExceptionalResultCode)
    {
      AddResponseProtocolOp response = message.getAddResponseProtocolOp();
      validateNoException(response.getResultCode(), response.getErrorMessage());
      return message;
    }
    return message;
  }

  private AddRequestProtocolOp addProtocolOp(AddRequest add)
  {
    return new AddRequestProtocolOp(bs(add.getName()), to(add.getAllAttributes()));
  }

  public void search(String baseDN, SearchScope scope, String filterString, String... attributes)
      throws IOException, LDAPException
  {
    search(newSearchRequest(baseDN, scope, filterString, attributes));
  }

  public void search(SearchRequest searchRequest) throws IOException, LDAPException
  {
    writeMessage(searchProtocolOp(searchRequest), to(searchRequest.getControls()));
  }

  private SearchRequestProtocolOp searchProtocolOp(SearchRequest r) throws LDAPException
  {
    return new SearchRequestProtocolOp(bs(r.getName()), r.getScope(), r.getDereferenceAliasesPolicy(),
        r.getSizeLimit(), r.getTimeLimit(), r.isTypesOnly(), to(r.getFilter()),
        new LinkedHashSet<>(r.getAttributes()));
  }

  public List<SearchResultEntryProtocolOp> readEntries() throws LDAPException, IOException
  {
    List<SearchResultEntryProtocolOp> entries = new ArrayList<>();
    LDAPMessage msg;
    while ((msg = readMessage()) != null)
    {
      ProtocolOp protocolOp = msg.getProtocolOp();
      if (protocolOp instanceof SearchResultDoneProtocolOp)
      {
        SearchResultDoneProtocolOp done = (SearchResultDoneProtocolOp) protocolOp;
        validateNoException(done.getResultCode(), done.getErrorMessage());
        return entries;
      }
      else if (protocolOp instanceof SearchResultEntryProtocolOp)
      {
        entries.add((SearchResultEntryProtocolOp) protocolOp);
      }
      else
      {
        throw new RuntimeException("Unexpected message " + protocolOp);
      }
    }
    return entries;
  }

  public LDAPMessage modify(ModifyRequest modifyRequest) throws IOException, LDAPException
  {
    return modify(modifyRequest, true);
  }

  public LDAPMessage modify(ModifyRequest modifyRequest, boolean throwOnExceptionalResultCode)
      throws IOException, LDAPException
  {
    writeMessage(modifyProtocolOp(modifyRequest), to(modifyRequest.getControls()));
    LDAPMessage message = readMessage();
    if (throwOnExceptionalResultCode)
    {
      ModifyResponseProtocolOp response = message.getModifyResponseProtocolOp();
      validateNoException(response.getResultCode(), response.getErrorMessage());
      return message;
    }
    return message;
  }

  private ProtocolOp modifyProtocolOp(ModifyRequest r)
  {
    return new ModifyRequestProtocolOp(bs(r.getName()), toRawModifications(r.getModifications()));
  }

  public ModifyDNResponseProtocolOp modifyDN(String entryDN, String newRDN, boolean deleteOldRDN)
      throws IOException, LDAPException
  {
    writeMessage(new ModifyDNRequestProtocolOp(bs(entryDN), bs(newRDN), deleteOldRDN));
    return readMessage().getModifyDNResponseProtocolOp();
  }

  public LDAPMessage modifyDN(ModifyDNRequest modifyDNRequest) throws IOException, LDAPException
  {
    return modifyDN(modifyDNRequest, true);
  }

  public LDAPMessage modifyDN(ModifyDNRequest modifyDNRequest, boolean throwOnExceptionalResultCode)
      throws IOException, LDAPException
  {
    writeMessage(modDNProtocolOp(modifyDNRequest), to(modifyDNRequest.getControls()));
    LDAPMessage message = readMessage();
    if (throwOnExceptionalResultCode)
    {
      ModifyDNResponseProtocolOp response = message.getModifyDNResponseProtocolOp();
      validateNoException(response.getResultCode(), response.getErrorMessage());
      return message;
    }
    return message;
  }

  private ModifyDNRequestProtocolOp modDNProtocolOp(ModifyDNRequest r)
  {
    return new ModifyDNRequestProtocolOp(
        bs(r.getName()), bs(r.getNewRDN()), r.isDeleteOldRDN(), bs(r.getNewSuperior()));
  }

  public LDAPMessage compare(CompareRequest compareRequest, boolean throwOnExceptionalResultCode)
      throws IOException, LDAPException
  {
    writeMessage(compareProtocolOp(compareRequest), to(compareRequest.getControls()));
    LDAPMessage message = readMessage();
    if (throwOnExceptionalResultCode)
    {
      CompareResponseProtocolOp response = message.getCompareResponseProtocolOp();
      validateNoException(response.getResultCode(), response.getErrorMessage());
      return message;
    }
    return message;
  }

  private CompareRequestProtocolOp compareProtocolOp(CompareRequest r)
  {
    return new CompareRequestProtocolOp(bs(r.getName()), r.getAttributeDescription().toString(), r.getAssertionValue());
  }

  public LDAPMessage delete(DeleteRequest deleteRequest) throws IOException, LDAPException
  {
    return delete(deleteRequest, true);
  }

  public LDAPMessage delete(DeleteRequest deleteRequest, boolean throwOnExceptionalResultCode)
      throws IOException, LDAPException
  {
    writeMessage(new DeleteRequestProtocolOp(bs(deleteRequest.getName())), to(deleteRequest.getControls()));
    LDAPMessage message = readMessage();
    if (throwOnExceptionalResultCode)
    {
      DeleteResponseProtocolOp response = message.getDeleteResponseProtocolOp();
      validateNoException(response.getResultCode(), response.getErrorMessage());
      return message;
    }
    return message;
  }

  public LDAPMessage extendedRequest(String oid) throws IOException, LDAPException
  {
    return extendedRequest(oid, null);
  }

  public LDAPMessage extendedRequest(String oid, ByteString requestValue)
      throws IOException, LDAPException
  {
    writeMessage(new ExtendedRequestProtocolOp(oid, requestValue));
    return readMessage();
  }

  private ByteString bs(Object o)
  {
    return o != null ? ByteString.valueOfObject(o) : null;
  }

  public void writeMessage(ProtocolOp protocolOp) throws IOException
  {
    writeMessage(protocolOp, (List<org.opends.server.types.Control>) null);
  }

  public void writeMessage(ProtocolOp protocolOp, List<org.opends.server.types.Control> controls) throws IOException
  {
    w.writeMessage(new LDAPMessage(messageID.getAndIncrement(), protocolOp, controls));
  }

  public void writeMessage(ProtocolOp protocolOp, org.opends.server.types.Control control) throws IOException
  {
    w.writeMessage(new LDAPMessage(messageID.getAndIncrement(), protocolOp, Arrays.asList(control)));
  }

  public LDAPMessage readMessage() throws IOException, LDAPException
  {
    final LDAPMessage message = r.readMessage();
    if (message != null)
    {
      return message;
    }
    throw new EOFException();
  }

  private void validateNoException(int resultCode, LocalizableMessage errorMessage) throws LdapException
  {
    ResultCode rc = ResultCode.valueOf(resultCode);
    if (rc.isExceptional())
    {
      throw LdapException.newLdapException(rc, errorMessage);
    }
  }

  public LDAPWriter getLdapWriter()
  {
    return this.w;
  }

  public LDAPAuthenticationHandler newLDAPAuthenticationHandler()
  {
    return new LDAPAuthenticationHandler(r, w, host, messageID);
  }

  @Override
  public void close() throws IOException
  {
    socket.close();
  }
}
