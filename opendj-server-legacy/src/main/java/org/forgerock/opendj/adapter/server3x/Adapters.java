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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.adapter.server3x;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.forgerock.opendj.ldap.AbstractSynchronousConnection;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SASLBindRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.SimpleBindRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.forgerock.opendj.ldap.ByteString.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.util.promise.Promises.*;

/** This class provides a connection factory and an adapter for the OpenDJ 2.x server. */
public final class Adapters {

    /** Constructor. */
    private Adapters() {
        // No implementation required.
    }

    /**
     * Returns a new root connection factory.
     *
     * @return A new root connection factory.
     */
    public static ConnectionFactory newRootConnectionFactory() {
        InternalClientConnection icc = InternalClientConnection.getRootConnection();
        return newConnectionFactory(icc);
    }

    /**
     * Returns a new connection factory.
     *
     * @param icc
     *            The internal client connection from server side.
     * @return A new SDK connection factory.
     */
    public static ConnectionFactory newConnectionFactory(final InternalClientConnection icc) {
        return new ConnectionFactory() {
            @Override
            public Promise<Connection, LdapException> getConnectionAsync() {
               try
              {
                return newResultPromise(getConnection());
              }
              catch (LdapException e)
              {
                return newExceptionPromise(e);
              }
            }

            @Override
            public Connection getConnection() throws LdapException {
                return newConnection(icc);
            }

            @Override
            public void close() {
                // Nothing to do.
            }
        };
    }

    /**
     * Returns a new connection.
     *
     * @param icc
     *            The internal client connection from server side.
     * @return A new SDK connection.
     */
    public static Connection newConnection(final InternalClientConnection icc) {
      return new AbstractSynchronousConnection() {

          @Override
          public Result search(final SearchRequest request, final SearchResultHandler handler)
                  throws LdapException {
              InternalSearchListener internalSearchListener = new InternalSearchListener() {

                  @Override
                  public void handleInternalSearchReference(
                          InternalSearchOperation searchOperation,
                          SearchResultReference searchReference) throws DirectoryException {
                      handler.handleReference(from(searchReference));
                  }

                  @Override
                  public void handleInternalSearchEntry(InternalSearchOperation searchOperation,
                          SearchResultEntry searchEntry) throws DirectoryException {
                      handler.handleEntry(from(searchEntry));
                  }
              };

              final SearchFilter filter = toSearchFilter(request.getFilter());
              final org.opends.server.protocols.internal.SearchRequest sr =
                  Requests.newSearchRequest(request.getName(), request.getScope(), filter)
                      .setDereferenceAliasesPolicy(request.getDereferenceAliasesPolicy())
                      .setSizeLimit(request.getSizeLimit())
                      .setTimeLimit(request.getTimeLimit())
                      .setTypesOnly(request.isTypesOnly())
                      .addAttribute(request.getAttributes())
                      .addControl(to(request.getControls()));
              return getResponseResult(icc.processSearch(sr, internalSearchListener));
          }

          @Override
          public void removeConnectionEventListener(ConnectionEventListener listener) {
              // Internal client connection don't have any connection events.
          }

          @Override
          public Result modifyDN(final ModifyDNRequest request) throws LdapException {
              return getResponseResult(icc.processModifyDN(request));
          }

          @Override
          public Result modify(final ModifyRequest request) throws LdapException {
              return getResponseResult(icc.processModify(request));
          }

          @Override
          public boolean isValid() {
              // Always true.
              return true;
          }

          @Override
          public boolean isClosed() {
              return false;
          }

          @Override
          public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request,
                  final IntermediateResponseHandler handler) throws LdapException {

              final ExtendedOperation extendedOperation =
                      icc.processExtendedOperation(request.getOID(), request.getValue(),
                              to(request.getControls()));

              final Result result = getResponseResult(extendedOperation);
              final GenericExtendedResult genericExtendedResult =
                      Responses.newGenericExtendedResult(result.getResultCode())
                               .setDiagnosticMessage(result.getDiagnosticMessage())
                               .setMatchedDN(result.getMatchedDN())
                               .setValue(extendedOperation.getResponseValue());
              try {
                  R extendedResult =
                          request.getResultDecoder().decodeExtendedResult(genericExtendedResult,
                                  new DecodeOptions());
                  for (final Control control : result.getControls()) {
                      extendedResult.addControl(control);
                  }
                  return extendedResult;

              } catch (DecodeException e) {
                  DN matchedDN = extendedOperation.getMatchedDN();
                  return request.getResultDecoder().newExtendedErrorResult(
                          extendedOperation.getResultCode(),
                          matchedDN != null ? matchedDN.toString() : null,
                          extendedOperation.getErrorMessage().toString());
              }
          }

          @Override
          public Result delete(final DeleteRequest request) throws LdapException {
              final DeleteOperation deleteOperation =
                      icc.processDelete(valueOfObject(request.getName()), to(request.getControls()));
              return getResponseResult(deleteOperation);
          }

          @Override
          public CompareResult compare(final CompareRequest request) throws LdapException {
              final CompareOperation compareOperation =
                      icc.processCompare(valueOfObject(request.getName()),
                              request.getAttributeDescription().toString(),
                              request.getAssertionValue(), to(request.getControls()));

              CompareResult result = Responses.newCompareResult(compareOperation.getResultCode());
              return getResponseResult(compareOperation, result);
          }

          @Override
          public void close(final UnbindRequest request, final String reason) {
              // no implementation in open-ds.
          }

          @Override
          public BindResult bind(final BindRequest request) throws LdapException {
              BindOperation bindOperation = null;
              if (request instanceof SimpleBindRequest) {
                  bindOperation =
                          icc.processSimpleBind(valueOfUtf8(request.getName()),
                                  ByteString.wrap(((SimpleBindRequest) request).getPassword()),
                                  to(request.getControls()));
              } else if (request instanceof SASLBindRequest) {
                  String serverName = null;
                  try {
                      serverName = InetAddress.getByName(null).getCanonicalHostName();
                  } catch (UnknownHostException e) {
                      // nothing to do.
                  }
                  BindClient bindClient = request.createBindClient(serverName);
                  do {
                      final GenericBindRequest genericBindRequest = bindClient.nextBindRequest();
                      bindOperation =
                              icc.processSASLBind(
                                      valueOfUtf8(request.getName()),
                                      ((SASLBindRequest) request).getSASLMechanism(),
                                      getCredentials(genericBindRequest.getAuthenticationValue()),
                                      to(request.getControls()));
                  } while (bindOperation.getResultCode() == ResultCode.SASL_BIND_IN_PROGRESS);

                  bindClient.dispose();

              } else { // not supported
                  throw newLdapException(Responses.newResult(ResultCode.AUTH_METHOD_NOT_SUPPORTED));
              }
              BindResult result = Responses.newBindResult(bindOperation.getResultCode());
              result.setServerSASLCredentials(bindOperation.getSASLCredentials());

              if (result.isSuccess()) {
                  return result;
              } else {
                  throw newLdapException(result);
              }
          }

          @Override
          public void addConnectionEventListener(ConnectionEventListener listener) {
              // Internal client connection don't have any connection events.
          }

          @Override
          public Result add(final AddRequest request) throws LdapException {
              final AddOperation addOperation =
                      icc.processAdd(valueOfObject(request.getName()), to(request
                              .getAllAttributes()), to(request.getControls()));
              return getResponseResult(addOperation);
          }

          @Override
          public String toString() {
              return icc.toString();
          }
      };
  }

}
