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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import static org.opends.server.config.ConfigConstants.DN_CONFIG_ROOT;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_AUTH_TYPE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_BIND_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ERROR_MESSAGE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_MATCHED_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_PROCESSING_TIME;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_REFERRAL_URLS;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_RESULT_CODE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_SASL_MECHANISM;
import static org.opends.server.loggers.AccessLogger.logBindRequest;
import static org.opends.server.loggers.AccessLogger.logBindResponse;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.messages.CoreMessages.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.*;
import org.opends.server.types.operation.PreParseBindOperation;
import org.opends.server.workflowelement.localbackend.*;




/**
 * This class defines an operation that may be used to authenticate a user to
 * the Directory Server.  Note that for security restrictions, response messages
 * that may be returned to the client must be carefully cleaned to ensure that
 * they do not provide a malicious client with information that may be useful in
 * an attack.  This does impact the debugability of the server, but that can
 * be addressed by calling the <CODE>setAuthFailureReason</CODE> method, which
 * can provide a reason for a failure in a form that will not be returned to the
 * client but may be written to a log file.
 */
public class BindOperationBasis
             extends AbstractOperation
             implements BindOperation, PreParseBindOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // The credentials used for SASL authentication.
  private ASN1OctetString saslCredentials;

  // The server SASL credentials provided to the client in the response.
  private ASN1OctetString serverSASLCredentials;

  // The authentication info for this bind operation.
  private AuthenticationInfo authInfo = null;

  // The authentication type used for this bind operation.
  private AuthenticationType authType;

  // The raw, unprocessed bind DN as contained in the client request.
  private ByteString rawBindDN;

  // The password used for simple authentication.
  private ByteString simplePassword;

  // The bind DN used for this bind operation.
  private DN bindDN;

  // The DN of the user entry that is attempting to authenticate.
  private DN userEntryDN;

  // The DN of the user as whom a SASL authentication was attempted (regardless
  // of whether the authentication was successful) for the purpose of updating
  // password policy state information.
  private Entry saslAuthUserEntry;

  // The set of response controls for this bind operation.
  private List<Control> responseControls;

  // A message explaining the reason for the authentication failure.
  private Message authFailureReason;

  // The SASL mechanism used for SASL authentication.
  private String saslMechanism;

  // A string representation of the protocol version for this bind operation.
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
    this.authType        = AuthenticationType.SIMPLE;
    this.saslMechanism   = null;
    this.saslCredentials = null;

    if (rawBindDN == null)
    {
      this.rawBindDN = new ASN1OctetString();
    }
    else
    {
      this.rawBindDN = rawBindDN;
    }

    if (simplePassword == null)
    {
      this.simplePassword = new ASN1OctetString();
    }
    else
    {
      this.simplePassword = simplePassword;
    }

    bindDN                   = null;
    userEntryDN              = null;
    responseControls         = new ArrayList<Control>(0);
    authFailureReason        = null;
    saslAuthUserEntry        = null;

    cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_BIND.get());
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
                       String saslMechanism, ASN1OctetString saslCredentials)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.protocolVersion = protocolVersion;
    this.authType        = AuthenticationType.SASL;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;
    this.simplePassword  = null;

    if (rawBindDN == null)
    {
      this.rawBindDN = new ASN1OctetString();
    }
    else
    {
      this.rawBindDN = rawBindDN;
    }

    bindDN                 = null;
    userEntryDN            = null;
    responseControls       = new ArrayList<Control>(0);
    authFailureReason      = null;
    saslAuthUserEntry      = null;

    cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_BIND.get());
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
    this.authType        = AuthenticationType.SIMPLE;
    this.bindDN          = bindDN;
    this.saslMechanism   = null;
    this.saslCredentials = null;

    if (bindDN == null)
    {
      rawBindDN = new ASN1OctetString();
    }
    else
    {
      rawBindDN = new ASN1OctetString(bindDN.toString());
    }

    if (simplePassword == null)
    {
      this.simplePassword = new ASN1OctetString();
    }
    else
    {
      this.simplePassword = simplePassword;
    }

    responseControls         = new ArrayList<Control>(0);
    authFailureReason        = null;
    saslAuthUserEntry        = null;
    userEntryDN              = null;

    cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_BIND.get());
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
                       String saslMechanism, ASN1OctetString saslCredentials)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.protocolVersion = protocolVersion;
    this.authType        = AuthenticationType.SASL;
    this.bindDN          = bindDN;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;
    this.simplePassword  = null;

    if (bindDN == null)
    {
      rawBindDN = new ASN1OctetString();
    }
    else
    {
      rawBindDN = new ASN1OctetString(bindDN.toString());
    }

    responseControls       = new ArrayList<Control>(0);
    authFailureReason      = null;
    saslAuthUserEntry      = null;
    userEntryDN            = null;

    cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_BIND.get());
  }


  /**
   * {@inheritDoc}
   */
  public final AuthenticationType getAuthenticationType()
  {
    return authType;
  }


  /**
   * {@inheritDoc}
   */
  public final ByteString getRawBindDN()
  {
    return rawBindDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawBindDN(ByteString rawBindDN)
  {
    if (rawBindDN == null)
    {
      this.rawBindDN = new ASN1OctetString();
    }
    else
    {
      this.rawBindDN = rawBindDN;
    }

    bindDN = null;
  }


  /**
   * {@inheritDoc}
   */
  public final DN getBindDN()
  {
    try
    {
      if (bindDN == null)
      {
        bindDN = DN.decode(rawBindDN);
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      setResultCode(ResultCode.INVALID_CREDENTIALS);
      setAuthFailureReason(de.getMessageObject());
    }
    return bindDN;
  }

  /**
   * {@inheritDoc}
   */
  public final ByteString getSimplePassword()
  {
    return simplePassword;
  }

  /**
   * {@inheritDoc}
   */
  public final void setSimplePassword(ByteString simplePassword)
  {
    if (simplePassword == null)
    {
      this.simplePassword = new ASN1OctetString();
    }
    else
    {
      this.simplePassword = simplePassword;
    }

    authType        = AuthenticationType.SIMPLE;
    saslMechanism   = null;
    saslCredentials = null;
  }

  /**
   * {@inheritDoc}
   */
  public final String getSASLMechanism()
  {
    return  saslMechanism;
  }

  /**
   * {@inheritDoc}
   */
  public final ASN1OctetString getSASLCredentials()
  {
    return saslCredentials;
  }

  /**
   * {@inheritDoc}
   */
  public final void setSASLCredentials(String saslMechanism,
                                       ASN1OctetString saslCredentials)
  {
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;

    authType       = AuthenticationType.SASL;
    simplePassword = null;
  }

  /**
   * {@inheritDoc}
   */
  public final ASN1OctetString getServerSASLCredentials()
  {
    return serverSASLCredentials;
  }

  /**
   * {@inheritDoc}
   */
  public final void setServerSASLCredentials(ASN1OctetString
                                                  serverSASLCredentials)
  {
    this.serverSASLCredentials = serverSASLCredentials;
  }

  /**
   * {@inheritDoc}
   */
  public final Entry getSASLAuthUserEntry()
  {
    return saslAuthUserEntry;
  }

  /**
   * {@inheritDoc}
   */
  public final void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    this.saslAuthUserEntry = saslAuthUserEntry;
  }

  /**
   * {@inheritDoc}
   */
  public final Message getAuthFailureReason()
  {
    return authFailureReason;
  }

  /**
   * {@inheritDoc}
   */
  public final void setAuthFailureReason(Message message)
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

  /**
   * {@inheritDoc}
   */
  public final DN getUserEntryDN()
  {
    return userEntryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final AuthenticationInfo getAuthenticationInfo()
  {
    return authInfo;
  }

  /**
   * {@inheritDoc}
   */
  public final void setAuthenticationInfo(AuthenticationInfo authInfo)
  {
    this.authInfo = authInfo;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.BIND;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    if (authType == AuthenticationType.SASL)
    {
      return new String[][]
      {
        new String[] { LOG_ELEMENT_BIND_DN, String.valueOf(rawBindDN) },
        new String[] { LOG_ELEMENT_AUTH_TYPE, authType.toString() },
        new String[] { LOG_ELEMENT_SASL_MECHANISM, saslMechanism }
      };
    }
    else
    {
      return new String[][]
      {
        new String[] { LOG_ELEMENT_BIND_DN, String.valueOf(rawBindDN) },
        new String[] { LOG_ELEMENT_AUTH_TYPE, authType.toString() }
      };
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String resultCode = String.valueOf(getResultCode().getIntValue());

    String errorMessage;
    MessageBuilder errorMessageBuffer = getErrorMessage();
    if (errorMessageBuffer == null)
    {
      errorMessage = null;
    }
    else
    {
      errorMessage = errorMessageBuffer.toString();
    }

    String matchedDNStr;
    DN matchedDN = getMatchedDN();
    if (matchedDN == null)
    {
      matchedDNStr = null;
    }
    else
    {
      matchedDNStr = matchedDN.toString();
    }

    String referrals;
    List<String> referralURLs = getReferralURLs();
    if ((referralURLs == null) || referralURLs.isEmpty())
    {
      referrals = null;
    }
    else
    {
      StringBuilder buffer = new StringBuilder();
      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      referrals = buffer.toString();
    }

    String processingTime =
         String.valueOf(getProcessingTime());

    return new String[][]
    {
      new String[] { LOG_ELEMENT_RESULT_CODE, resultCode },
      new String[] { LOG_ELEMENT_ERROR_MESSAGE, errorMessage },
      new String[] { LOG_ELEMENT_MATCHED_DN, matchedDNStr },
      new String[] { LOG_ELEMENT_REFERRAL_URLS, referrals },
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }


  /**
   * {@inheritDoc}
   */
  @Override()
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

  /**
   * {@inheritDoc}
   */
  public void setUserEntryDN(DN userEntryDN)
  {
    this.userEntryDN = userEntryDN;
  }

  /**
   * {@inheritDoc}
   */
  public String getProtocolVersion()
  {
    return protocolVersion;
  }

  /**
   * {@inheritDoc}
   */
  public void setProtocolVersion(String protocolVersion)
  {
    this.protocolVersion = protocolVersion;
  }

  /**
   * {@inheritDoc}
   */
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer and initially set the result to indicate that
    // the result is unknown.
    setProcessingStartTime();

    // Log the bind request message.
    logBindRequest(this);

    ClientConnection clientConnection = getClientConnection();

    // Set a flag to indicate that a bind operation is in progress.  This should
    // ensure that no new operations will be accepted for this client until the
    // bind is complete.
    clientConnection.setBindInProgress(true);

    // Wipe out any existing authentication for the client connection and create
    // a placeholder that will be used if the bind is successful.
    clientConnection.setUnauthenticated();

    // Abandon any operations that may be in progress for the client.
    Message cancelReason = INFO_CANCELED_BY_BIND_REQUEST.get();
    CancelRequest cancelRequest = new CancelRequest(true, cancelReason);
    clientConnection.cancelAllOperationsExcept(cancelRequest, getMessageID());


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
        DirectoryServer.getPluginConfigManager();


    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;


    try
    {
      // Invoke the pre-parse bind plugins.
      PluginResult.PreParse preParseResult =
          pluginConfigManager.invokePreParseBindPlugins(this);
      if (!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        return;
      }


      // Process the bind DN to convert it from the raw form as provided by the
      // client into the form required for the rest of the bind processing.
      DN bindDN = getBindDN();
      if (bindDN == null){
        return;
      }

      // If this is a simple bind
      // Then check wether the bind DN is actually one of the alternate root DNs
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


      // Special case to manage RootDNs
      // RootDNs are stored in cn=config but this workflow is not
      // available through non-admin network groups.
      // So if the bind DN is in cn=config, we directly retrieve
      // the workflow handling cn=config
      // FIXME: it would be better to store RootDNs in a separate backend.
      // Issue #3502 has been logged to track this request.
      boolean isInConfig;
      try {
        isInConfig = bindDN.isDescendantOf(DN.decode(DN_CONFIG_ROOT));
      } catch (DirectoryException ex) {
        // can not happen
        isInConfig = false;
      }

      Workflow workflow;
      if (isInConfig) {
        workflow = WorkflowImpl.getWorkflow("__config.ldif__#cn=config");
      } else {
        // Retrieve the network group attached to the client connection
        // and get a workflow to process the operation.
        NetworkGroup ng = getClientConnection().getNetworkGroup();
        workflow = ng.getWorkflowCandidate(bindDN);
      }
      if (workflow == null)
      {
        // We have found no workflow for the requested base DN, just return
        // a no such entry result code and stop the processing.
        updateOperationErrMsgAndResCode();
        return;
      }
      workflow.execute(this);
      workflowExecuted = true;

    }
    catch(CanceledOperationException coe)
    {
      // This shouldn't happen for bind operations. Just cancel anyways
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, coe);
      }

      setResultCode(ResultCode.CANCELED);

      appendErrorMessage(cancelRequest.getCancelReason());
    }
    finally
    {
      // If the bind processing is finished, then unset the "bind in progress"
      // flag to allow other operations to be processed on the connection.
      if (getResultCode() != ResultCode.SASL_BIND_IN_PROGRESS)
      {
        clientConnection.setBindInProgress(false);
      }

      // Stop the processing timer.
      setProcessingStopTime();

      // Send the bind response to the client.
      clientConnection.sendResponse(this);

      // Log the bind response.
      logBindResponse(this);

      // Invoke the post-response bind plugins.
      invokePostResponsePlugins(workflowExecuted);
    }
  }


  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the worklfow, otherwise invoke the post reponse plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been
   *                         executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // The post responses are provided by the workflow elements of the
      // workflow.
      List localOperations =
        (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);
      if (localOperations != null)
      {
        for (Object localOp : localOperations)
        {
          LocalBackendBindOperation localOperation =
            (LocalBackendBindOperation)localOp;
          // Invoke the post-response bind plugins.
          pluginConfigManager.invokePostResponseBindPlugins(localOperation);
        }
      }
      else
      {
        // The current operation does not implement any bind post response
        // interface so we cannot invoke any post-response plugin.
      }
    }
  }


  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflows were found to process
   * the operation.
   */
  private void updateOperationErrMsgAndResCode()
  {
    Message message = ERR_BIND_OPERATION_UNKNOWN_USER.get(
            String.valueOf(getBindDN()));
    setResultCode(ResultCode.INVALID_CREDENTIALS);
    setAuthFailureReason(message);
  }

}

