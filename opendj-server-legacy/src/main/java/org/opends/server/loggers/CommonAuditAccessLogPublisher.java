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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.LoggerMessages.*;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.resource.Requests.newCreateRequest;
import static org.forgerock.json.resource.ResourcePath.resourcePath;
import static org.opends.server.loggers.CommonAudit.DEFAULT_TRANSACTION_ID;
import static org.opends.server.loggers.OpenDJAccessAuditEventBuilder.openDJAccessEvent;
import static org.opends.server.types.AuthenticationType.SASL;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.audit.events.AccessAuditEventBuilder.ResponseStatus;
import org.forgerock.audit.events.AuditEvent;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;
import org.forgerock.opendj.server.config.server.AccessLogPublisherCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.controls.TransactionIdControl;
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.core.UnbindOperation;
import org.opends.server.types.AuthenticationInfo;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.util.StaticUtils;

/**
 * Publishes access events to Common Audit.
 *
 * @param <T> the type of configuration
 */
abstract class CommonAuditAccessLogPublisher<T extends AccessLogPublisherCfg>
  extends AbstractTextAccessLogPublisher<T>
  implements CommonAuditLogPublisher
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Audit service handler. */
  private RequestHandler requestHandler;

  /** Current configuration for this publisher. */
  private T config;

  private ServerContext serverContext;

  @Override
  public void setRequestHandler(RequestHandler handler)
  {
    this.requestHandler = handler;
  }

  abstract boolean shouldLogControlOids();

  T getConfig()
  {
    return config;
  }

  void setConfig(T config)
  {
    this.config = config;
  }

  @Override
  public void initializeLogPublisher(final T cfg, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    this.serverContext = serverContext;
    initializeFilters(cfg);
    config = cfg;
  }

  @Override
  public boolean isConfigurationAcceptable(final T configuration, final List<LocalizableMessage> unacceptableReasons)
  {
    return isFilterConfigurationAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public void logAbandonResult(final AbandonOperation abandonOperation)
  {
    if (!isResponseLoggable(abandonOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(abandonOperation, "ABANDON");
    addResultCodeAndMessage(abandonOperation, builder);
    appendAbandonRequest(abandonOperation, builder);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logAddResponse(final AddOperation addOperation)
  {
    if (!isResponseLoggable(addOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(addOperation, "ADD");
    addResultCodeAndMessage(addOperation, builder);
    appendAddRequest(addOperation, builder);
    DN proxiedAuthorizationDN = addOperation.getProxiedAuthorizationDN();
    appendProxiedAuthorizationDNIfNeeded(builder, proxiedAuthorizationDN);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logBindResponse(final BindOperation bindOperation)
  {
    if (!isResponseLoggable(bindOperation))
    {
      return;
    }

    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(bindOperation, "BIND");
    addResultCodeAndMessage(bindOperation, builder);
    appendBindRequest(bindOperation, builder);

    final LocalizableMessage failureMessage = bindOperation.getAuthFailureReason();
    if (failureMessage != null)
    {
      // this code path is mutually exclusive with the if result code is success
      // down below
      builder.ldapFailureMessage(failureMessage.toString());
      if (bindOperation.getSASLMechanism() != null && bindOperation.getSASLAuthUserEntry() != null)
      { // SASL bind and we have successfully found a user entry for auth
        builder.userId(bindOperation.getSASLAuthUserEntry().getName().toString());
      }
      else
      { // SASL bind failed to find user entry for auth or simple bind
        builder.userId(bindOperation.getRawBindDN().toString());
      }
    }

    if (bindOperation.getResultCode() == ResultCode.SUCCESS)
    {
      // this code path is mutually exclusive with the if failure message exist
      // just above
      final AuthenticationInfo authInfo = bindOperation.getAuthenticationInfo();
      if (authInfo != null)
      {
        final DN authDN = authInfo.getAuthenticationDN();
        if (authDN != null)
        {
          builder.userId(authDN.toString());

          final DN authzDN = authInfo.getAuthorizationDN();
          if (!authDN.equals(authzDN))
          {
            builder.runAs(authzDN == null ? "" : authzDN.toString());
          }
        }
        else
        {
          builder.userId("");
        }
      }
    }

    sendEvent(builder.toEvent());
  }

  @Override
  public void logCompareResponse(final CompareOperation compareOperation)
  {
    if (!isResponseLoggable(compareOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(compareOperation, "COMPARE");
    addResultCodeAndMessage(compareOperation, builder);
    appendCompareRequest(compareOperation, builder);
    DN proxiedAuthorizationDN = compareOperation.getProxiedAuthorizationDN();
    appendProxiedAuthorizationDNIfNeeded(builder, proxiedAuthorizationDN);

    sendEvent(builder.toEvent());
  }

  private void appendProxiedAuthorizationDNIfNeeded(OpenDJAccessAuditEventBuilder<?> builder, DN proxiedAuthorizationDN)
  {
    if (proxiedAuthorizationDN != null)
    {
      builder.runAs(proxiedAuthorizationDN.toString());
    }
  }

  @Override
  public void logConnect(final ClientConnection clientConnection)
  {
    if (!isConnectLoggable(clientConnection))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = openDJAccessEvent()
        .client(clientConnection.getClientAddress(), clientConnection.getClientPort())
        .server(clientConnection.getServerAddress(), clientConnection.getServerPort())
        .request(clientConnection.getProtocol(), "CONNECT")
        .transactionId(DEFAULT_TRANSACTION_ID)
        .response(ResponseStatus.SUCCESSFUL, String.valueOf(ResultCode.SUCCESS.intValue()), 0, TimeUnit.MILLISECONDS)
        .ldapConnectionId(clientConnection.getConnectionID());

    sendEvent(builder.toEvent());
  }

  @Override
  public void logDeleteResponse(final DeleteOperation deleteOperation)
  {
    if (!isResponseLoggable(deleteOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(deleteOperation, "DELETE");
    addResultCodeAndMessage(deleteOperation, builder);
    appendDeleteRequest(deleteOperation, builder);
    DN proxiedAuthorizationDN = deleteOperation.getProxiedAuthorizationDN();
    appendProxiedAuthorizationDNIfNeeded(builder, proxiedAuthorizationDN);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logDisconnect(final ClientConnection clientConnection, final DisconnectReason disconnectReason,
      final LocalizableMessage message)
  {
    if (!isDisconnectLoggable(clientConnection))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = openDJAccessEvent()
        .client(clientConnection.getClientAddress(), clientConnection.getClientPort())
        .server(clientConnection.getServerAddress(), clientConnection.getServerPort())
        .request(clientConnection.getProtocol(), "DISCONNECT")
        .transactionId(DEFAULT_TRANSACTION_ID)
        .response(ResponseStatus.SUCCESSFUL, String.valueOf(ResultCode.SUCCESS.intValue()), 0, TimeUnit.MILLISECONDS)
        .ldapConnectionId(clientConnection.getConnectionID())
        .ldapReason(disconnectReason)
        .ldapMessage(message);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logExtendedResponse(final ExtendedOperation extendedOperation)
  {
    if (!isResponseLoggable(extendedOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(extendedOperation, "EXTENDED");
    addResultCodeAndMessage(extendedOperation, builder);
    appendExtendedRequest(extendedOperation, builder);
    final String oid = extendedOperation.getResponseOID();
    if (oid != null)
    {
      final ExtendedOperationHandler<?> extOpHandler = DirectoryServer.getExtendedOperationHandler(oid);
      if (extOpHandler != null)
      {
        String name = extOpHandler.getExtendedOperationName();
        builder.ldapName(name);
      }
      builder.ldapOid(oid);
    }
    sendEvent(builder.toEvent());
  }

  @Override
  public void logModifyDNResponse(final ModifyDNOperation modifyDNOperation)
  {
    if (!isResponseLoggable(modifyDNOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(modifyDNOperation, "MODIFYDN");
    addResultCodeAndMessage(modifyDNOperation, builder);
    appendModifyDNRequest(modifyDNOperation, builder);
    DN proxiedAuthorizationDN = modifyDNOperation.getProxiedAuthorizationDN();
    appendProxiedAuthorizationDNIfNeeded(builder, proxiedAuthorizationDN);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logModifyResponse(final ModifyOperation modifyOperation)
  {
    if (!isResponseLoggable(modifyOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(modifyOperation, "MODIFY");
    addResultCodeAndMessage(modifyOperation, builder);
    appendModifyRequest(modifyOperation, builder);
    DN proxiedAuthorizationDN = modifyOperation.getProxiedAuthorizationDN();
    appendProxiedAuthorizationDNIfNeeded(builder, proxiedAuthorizationDN);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logSearchResultDone(final SearchOperation searchOperation)
  {
    if (!isResponseLoggable(searchOperation))
    {
      return;
    }
    OpenDJAccessAuditEventBuilder<?> builder = getEventBuilder(searchOperation, "SEARCH");
    addResultCodeAndMessage(searchOperation, builder);
    builder.ldapSearch(searchOperation).ldapNEntries(searchOperation.getEntriesSent());
    DN proxiedAuthorizationDN = searchOperation.getProxiedAuthorizationDN();
    appendProxiedAuthorizationDNIfNeeded(builder, proxiedAuthorizationDN);

    sendEvent(builder.toEvent());
  }

  @Override
  public void logUnbind(final UnbindOperation unbindOperation)
  {
    if (!isRequestLoggable(unbindOperation))
    {
      return;
    }
    sendEvent(getEventBuilder(unbindOperation, "UNBIND").toEvent());
  }

  @Override
  protected void close0()
  {
    // nothing to do because closing is managed in the CommonAudit class
  }

  private void appendAbandonRequest(final AbandonOperation abandonOperation,
      final OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapIdToAbandon(abandonOperation.getIDToAbandon());
  }

  private void appendAddRequest(final AddOperation addOperation, OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapDn(addOperation.getRawEntryDN().toString());
  }

  private void appendBindRequest(final BindOperation bindOperation, final OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapProtocolVersion(bindOperation.getProtocolVersion());
    final String authType = bindOperation.getAuthenticationType() != SASL ?
        bindOperation.getAuthenticationType().toString() : "SASL mechanism=" + bindOperation.getSASLMechanism();
    builder.ldapAuthType(authType);

    builder.ldapDn(bindOperation.getRawBindDN().toString());
  }

  private void appendCompareRequest(final CompareOperation compareOperation,
      final OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapDn(compareOperation.getRawEntryDN().toString());
    builder.ldapAttr(compareOperation.getAttributeDescription().getAttributeType().getNameOrOID());
  }

  private void appendDeleteRequest(final DeleteOperation deleteOperation,
      final OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapDn(deleteOperation.getRawEntryDN().toString());
  }

  private void appendExtendedRequest(final ExtendedOperation extendedOperation,
      final OpenDJAccessAuditEventBuilder<?> builder)
  {
    final String oid = extendedOperation.getRequestOID();
    final ExtendedOperationHandler<?> extOpHandler = DirectoryServer.getExtendedOperationHandler(oid);
    if (extOpHandler != null)
    {
      final String name = extOpHandler.getExtendedOperationName();
      builder.ldapName(name);
    }
    builder.ldapOid(oid);
  }

  private void appendModifyDNRequest(final ModifyDNOperation modifyDNOperation,
      final OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapDn(modifyDNOperation.getRawEntryDN().toString());
    builder.ldapModifyDN(modifyDNOperation);
  }

  private void appendModifyRequest(final ModifyOperation modifyOperation,
      final OpenDJAccessAuditEventBuilder<?> builder)
  {
    builder.ldapDn(modifyOperation.getRawEntryDN().toString());
  }

  private OpenDJAccessAuditEventBuilder<?> addResultCodeAndMessage(
      Operation operation, OpenDJAccessAuditEventBuilder<?> builder)
  {
    final LocalizableMessageBuilder message = operation.getErrorMessage();
    int resultCode = operation.getResultCode().intValue();
    ResponseStatus status = resultCode == 0 ? ResponseStatus.SUCCESSFUL : ResponseStatus.FAILED;
    Pair<Long, TimeUnit> executionTime = getExecutionTime(operation);
    if (message != null && message.length() > 0)
    {
      builder.responseWithDetail(status, String.valueOf(resultCode), executionTime.getFirst(),
          executionTime.getSecond(), json(message.toString()));
    }
    else
    {
      builder.response(status, String.valueOf(resultCode), executionTime.getFirst(), executionTime.getSecond());
    }
    if (shouldLogControlOids())
    {
      builder.ldapResponseControls(operation);
    }
    builder.ldapMaskedResultAndMessage(operation)
        .ldapAdditionalItems(operation);
    return builder;
  }

  /** Returns an event builder with all common fields filled. */
  private OpenDJAccessAuditEventBuilder<?> getEventBuilder(final Operation operation, final String opType)
  {
    ClientConnection clientConn = operation.getClientConnection();

    OpenDJAccessAuditEventBuilder<?> builder = openDJAccessEvent()
      .client(clientConn.getClientAddress(), clientConn.getClientPort())
      .server(clientConn.getServerAddress(), clientConn.getServerPort())
      .request(clientConn.getProtocol(), opType)
      .ldapSync(operation)
      .ldapIds(operation)
      .transactionId(getTransactionId(operation));

    if (shouldLogControlOids())
    {
      builder.ldapRequestControls(operation);
    }
    return builder;
  }

  private String getTransactionId(Operation operation)
  {
    String transactionId = getTransactionIdFromControl(operation);
    if (transactionId == null || !serverContext.getCommonAudit().shouldTrustTransactionIds())
    {
      // use a default value
      transactionId = DEFAULT_TRANSACTION_ID;
    }
    return transactionId;
  }

  private String getTransactionIdFromControl(Operation operation)
  {
    try
    {
      TransactionIdControl control = operation.getRequestControl(TransactionIdControl.DECODER);
      return control != null ? control.getTransactionId() : null;
    }
    catch (DirectoryException e)
    {
      logger.error(ERR_COMMON_AUDIT_INVALID_TRANSACTION_ID.get(StaticUtils.stackTraceToSingleLineString(e)));
    }
    return null;
  }

  private Pair<Long,TimeUnit> getExecutionTime(final Operation operation)
  {
    Long etime = operation.getProcessingNanoTime();
    // if not configured for nanos, use millis
    return etime <= -1 ?
        Pair.of(operation.getProcessingTime(), TimeUnit.MILLISECONDS) :
        Pair.of(etime, TimeUnit.NANOSECONDS);
  }

  /** Sends an JSON-encoded event to the audit service. */
  private void sendEvent(AuditEvent event)
  {
    CreateRequest request = newCreateRequest(resourcePath("/ldap-access"), event.getValue());
    requestHandler
      .handleCreate(new RootContext(), request)
      .thenOnException(new ExceptionHandler<ResourceException>()
        {
          @Override
          public void handleException(ResourceException e)
          {
            logger.error(ERR_COMMON_AUDIT_UNABLE_TO_PROCESS_LOG_EVENT.get(StaticUtils.stackTraceToSingleLineString(e)));
          }
        })
      .thenOnRuntimeException(new RuntimeExceptionHandler()
        {
          @Override
          public void handleRuntimeException(RuntimeException e)
          {
            logger.error(ERR_COMMON_AUDIT_UNABLE_TO_PROCESS_LOG_EVENT.get(StaticUtils.stackTraceToSingleLineString(e)));
          }
        });
  }
}
