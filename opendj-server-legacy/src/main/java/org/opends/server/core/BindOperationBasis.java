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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.*;
import org.opends.server.types.operation.PreParseBindOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendBindOperation;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation that may be used to authenticate a user to
 * the Directory Server.  Note that for security restrictions, response messages
 * that may be returned to the client must be carefully cleaned to ensure that
 * they do not provide a malicious client with information that may be useful in
 * an attack.  This does impact the debuggability of the server, but that can
 * be addressed by calling the <CODE>setAuthFailureReason</CODE> method, which
 * can provide a reason for a failure in a form that will not be returned to the
 * client but may be written to a log file.
 */
public class BindOperationBasis
             extends AbstractOperation
             implements BindOperation, PreParseBindOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The credentials used for SASL authentication. */
  private ByteString saslCredentials;

  /** The server SASL credentials provided to the client in the response. */
  private ByteString serverSASLCredentials;

  /** The authentication info for this bind operation. */
  private AuthenticationInfo authInfo;

  /** The authentication type used for this bind operation. */
  private AuthenticationType authType;

  /** The raw, unprocessed bind DN as contained in the client request. */
  private ByteString rawBindDN;

  /** The password used for simple authentication. */
  private ByteString simplePassword;

  /** The bind DN used for this bind operation. */
  private DN bindDN;

  /** The DN of the user entry that is attempting to authenticate. */
  private DN userEntryDN;

  /**
   * The DN of the user as whom a SASL authentication was attempted (regardless
   * of whether the authentication was successful) for the purpose of updating
   * password policy state information.
   */
  private Entry saslAuthUserEntry;

  /** The set of response controls for this bind operation. */
  private final List<Control> responseControls = new ArrayList<>(0);

  /** A message explaining the reason for the authentication failure. */
  private LocalizableMessage authFailureReason;

  /** The SASL mechanism used for SASL authentication. */
  private String saslMechanism;

  /** A string representation of the protocol version for this bind operation. */
  private String protocolVersion;

  /**
   * Creates a new simple bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  protocolVersion   The string representation of the protocol version
   *                           associated with this bind request.
   * @param  rawBindDN         The raw, unprocessed bind DN as provided in the
   *                           request from the client.
   * @param  simplePassword    The password to use for the simple
   *                           authentication.
   */
  public BindOperationBasis(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls,
                       String protocolVersion, ByteString rawBindDN,
                       ByteString simplePassword)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.protocolVersion = protocolVersion;

    setRawBindDN(rawBindDN);
    setSimplePassword(simplePassword);

    cancelResult = getBindCancelResult();
  }

  /**
   * Creates a new SASL bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  protocolVersion   The string representation of the protocol version
   *                           associated with this bind request.
   * @param  rawBindDN         The raw, unprocessed bind DN as provided in the
   *                           request from the client.
   * @param  saslMechanism     The SASL mechanism included in the request.
   * @param  saslCredentials   The optional SASL credentials included in the
   *                           request.
   */
  public BindOperationBasis(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls,
                       String protocolVersion, ByteString rawBindDN,
                       String saslMechanism, ByteString saslCredentials)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.protocolVersion = protocolVersion;
    this.authType        = AuthenticationType.SASL;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;

    setRawBindDN(rawBindDN);

    cancelResult = getBindCancelResult();
  }

  /**
   * Creates a new simple bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  protocolVersion   The string representation of the protocol version
   *                           associated with this bind request.
   * @param  bindDN            The bind DN for this bind operation.
   * @param  simplePassword    The password to use for the simple
   *                           authentication.
   */
  public BindOperationBasis(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls,
                       String protocolVersion, DN bindDN,
                       ByteString simplePassword)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.protocolVersion = protocolVersion;
    this.bindDN          = bindDN;

    rawBindDN = computeRawBindDN(bindDN);

    setSimplePassword(simplePassword);

    cancelResult = getBindCancelResult();
  }

  /**
   * Creates a new SASL bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  protocolVersion   The string representation of the protocol version
   *                           associated with this bind request.
   * @param  bindDN            The bind DN for this bind operation.
   * @param  saslMechanism     The SASL mechanism included in the request.
   * @param  saslCredentials   The optional SASL credentials included in the
   *                           request.
   */
  public BindOperationBasis(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls,
                       String protocolVersion, DN bindDN,
                       String saslMechanism, ByteString saslCredentials)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.protocolVersion = protocolVersion;
    this.authType        = AuthenticationType.SASL;
    this.bindDN          = bindDN;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;

    rawBindDN = computeRawBindDN(bindDN);

    cancelResult = getBindCancelResult();
  }

  private ByteString computeRawBindDN(DN bindDN)
  {
    if (bindDN != null)
    {
      return ByteString.valueOfUtf8(bindDN.toString());
    }
    return ByteString.empty();
  }

  private CancelResult getBindCancelResult()
  {
    return new CancelResult(CANNOT_CANCEL, ERR_CANNOT_CANCEL_BIND.get());
  }

  @Override
  public DN getProxiedAuthorizationDN()
  {
    return null;
  }

  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
  }

  @Override
  public final AuthenticationType getAuthenticationType()
  {
    return authType;
  }

  @Override
  public final ByteString getRawBindDN()
  {
    return rawBindDN;
  }

  @Override
  public final void setRawBindDN(ByteString rawBindDN)
  {
    if (rawBindDN != null)
    {
      this.rawBindDN = rawBindDN;
    }
    else
    {
      this.rawBindDN = ByteString.empty();
    }

    bindDN = null;
  }

  @Override
  public final DN getBindDN()
  {
    try
    {
      if (bindDN == null)
      {
        bindDN = DN.valueOf(rawBindDN);
      }
    }
    catch (LocalizedIllegalArgumentException e)
    {
      logger.traceException(e);

      setResultCode(ResultCode.INVALID_CREDENTIALS);
      setAuthFailureReason(e.getMessageObject());
    }
    return bindDN;
  }

  @Override
  public final ByteString getSimplePassword()
  {
    return simplePassword;
  }

  @Override
  public final void setSimplePassword(ByteString simplePassword)
  {
    if (simplePassword != null)
    {
      this.simplePassword = simplePassword;
    }
    else
    {
      this.simplePassword = ByteString.empty();
    }

    authType        = AuthenticationType.SIMPLE;
    saslMechanism   = null;
    saslCredentials = null;
  }

  @Override
  public final String getSASLMechanism()
  {
    return saslMechanism;
  }

  @Override
  public final ByteString getSASLCredentials()
  {
    return saslCredentials;
  }

  @Override
  public final void setSASLCredentials(String saslMechanism,
                                       ByteString saslCredentials)
  {
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;

    authType       = AuthenticationType.SASL;
    simplePassword = null;
  }

  @Override
  public final ByteString getServerSASLCredentials()
  {
    return serverSASLCredentials;
  }

  @Override
  public final void setServerSASLCredentials(ByteString serverSASLCredentials)
  {
    this.serverSASLCredentials = serverSASLCredentials;
  }

  @Override
  public final Entry getSASLAuthUserEntry()
  {
    return saslAuthUserEntry;
  }

  @Override
  public final void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    this.saslAuthUserEntry = saslAuthUserEntry;
  }

  @Override
  public final LocalizableMessage getAuthFailureReason()
  {
    return authFailureReason;
  }

  @Override
  public final void setAuthFailureReason(LocalizableMessage message)
  {
    if (DirectoryServer.returnBindErrorMessages())
    {
      appendErrorMessage(message);
    }
    else
    {
      authFailureReason = message;
    }
  }

  @Override
  public final DN getUserEntryDN()
  {
    return userEntryDN;
  }

  @Override
  public final AuthenticationInfo getAuthenticationInfo()
  {
    return authInfo;
  }

  @Override
  public final void setAuthenticationInfo(AuthenticationInfo authInfo)
  {
    this.authInfo = authInfo;
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.
    return OperationType.BIND;
  }

  @Override
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  @Override
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  @Override
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("BindOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", protocol=\"");
    buffer.append(clientConnection.getProtocol());
    buffer.append(" ");
    buffer.append(protocolVersion);
    buffer.append(", dn=");
    buffer.append(rawBindDN);
    buffer.append(", authType=");
    buffer.append(authType);
    buffer.append(")");
  }

  @Override
  public void setUserEntryDN(DN userEntryDN)
  {
    this.userEntryDN = userEntryDN;
  }

  @Override
  public String getProtocolVersion()
  {
    return protocolVersion;
  }

  @Override
  public void setProtocolVersion(String protocolVersion)
  {
    this.protocolVersion = protocolVersion;
  }

  @Override
  public final void run()
  {
    // Start the processing timer and initially set the result to indicate that
    // the result is unknown.
    setResultCode(ResultCode.UNDEFINED);
    setProcessingStartTime();

    logBindRequest(this);

    // Wipe out any existing authentication for the client connection and create
    // a placeholder that will be used if the bind is successful.
    ClientConnection clientConnection = getClientConnection();
    clientConnection.setUnauthenticated();

    // Abandon any operations that may be in progress for the client.
    LocalizableMessage cancelReason = INFO_CANCELED_BY_BIND_REQUEST.get();
    CancelRequest cancelRequest = new CancelRequest(true, cancelReason);
    clientConnection.cancelAllOperationsExcept(cancelRequest, getMessageID());

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;
    try
    {
      // Invoke the pre-parse bind plugins.
      if (!processOperationResult(getPluginConfigManager().invokePreParseBindPlugins(this)))
      {
        return;
      }

      // Process the bind DN to convert it from the raw form as provided by the
      // client into the form required for the rest of the bind processing.
      DN bindDN = getBindDN();
      if (bindDN == null){
        return;
      }

      // If this is a simple bind
      // Then check whether the bind DN is actually one of the alternate root DNs
      // defined in the server.  If so, then replace it with the actual DN
      // for that user.
      switch (getAuthenticationType())
      {
        case SIMPLE:
          DN actualRootDN = DirectoryServer.getActualRootBindDN(bindDN);
          if (actualRootDN != null)
          {
            bindDN = actualRootDN;
          }
      }

      workflowExecuted = execute(this, bindDN);
    }
    catch(CanceledOperationException coe)
    {
      // This shouldn't happen for bind operations. Just cancel anyways
      logger.traceException(coe);

      setResultCode(ResultCode.CANCELLED);

      appendErrorMessage(cancelRequest.getCancelReason());
    }
    finally
    {
      setProcessingStopTime();
      logBindResponse(this);

      // Send the bind response to the client.
      clientConnection.sendResponse(this);

      // If the bind processing is finished, then unset the "bind in progress"
      // flag to allow other operations to be processed on the connection.
      if (getResultCode() != ResultCode.SASL_BIND_IN_PROGRESS)
      {
        clientConnection.finishSaslBind();
      }
      clientConnection.finishBind();

      invokePostResponsePlugins(workflowExecuted);
    }
  }

  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the workflow, otherwise invoke the post response plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // The post responses are provided by the workflow elements of the workflow.
      List localOperations = (List) getAttachment(Operation.LOCALBACKENDOPERATIONS);
      if (localOperations != null)
      {
        for (Object localOp : localOperations)
        {
          LocalBackendBindOperation localOperation = (LocalBackendBindOperation) localOp;
          // Invoke the post-response bind plugins.
          getPluginConfigManager().invokePostResponseBindPlugins(localOperation);
        }
      }
      else
      {
        // The current operation does not implement any bind post response
        // interface so we cannot invoke any post-response plugin.
      }
    }
  }

  @Override
  public void updateOperationErrMsgAndResCode()
  {
    LocalizableMessage message = ERR_BIND_OPERATION_UNKNOWN_USER.get();
    setResultCode(ResultCode.INVALID_CREDENTIALS);
    setAuthFailureReason(message);
  }
}
