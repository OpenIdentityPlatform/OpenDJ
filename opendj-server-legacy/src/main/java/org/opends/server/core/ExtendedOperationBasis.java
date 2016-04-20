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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.PostOperationExtendedOperation;
import org.opends.server.types.operation.PostResponseExtendedOperation;
import org.opends.server.types.operation.PreOperationExtendedOperation;
import org.opends.server.types.operation.PreParseExtendedOperation;

/** This class defines an extended operation, which can perform virtually any kind of task. */
public class ExtendedOperationBasis
       extends AbstractOperation
       implements ExtendedOperation,
                  PreParseExtendedOperation,
                  PreOperationExtendedOperation,
                  PostOperationExtendedOperation,
                  PostResponseExtendedOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The value for the request associated with this extended operation. */
  private ByteString requestValue;

  /** The value for the response associated with this extended operation. */
  private ByteString responseValue;

  /** The set of response controls for this extended operation. */
  private List<Control> responseControls;

  /** The OID for the request associated with this extended operation. */
  private String requestOID;

  /** The OID for the response associated with this extended operation. */
  private String responseOID;

  /**
   * Creates a new extended operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  requestOID        The OID for the request associated with this
   *                           extended operation.
   * @param  requestValue      The value for the request associated with this
   *                           extended operation.
   */
  public ExtendedOperationBasis(ClientConnection clientConnection,
                           long operationID,
                           int messageID, List<Control> requestControls,
                           String requestOID, ByteString requestValue)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.requestOID   = requestOID;
    this.requestValue = requestValue;

    responseOID      = null;
    responseValue    = null;
    responseControls = new ArrayList<>();
    cancelRequest    = null;

    if (requestOID.equals(OID_CANCEL_REQUEST))
    {
      cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
          ERR_CANNOT_CANCEL_CANCEL.get());
    }
    if(requestOID.equals(OID_START_TLS_REQUEST))
    {
      cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
          ERR_CANNOT_CANCEL_START_TLS.get());
    }
  }

  @Override
  public final String getRequestOID()
  {
    return requestOID;
  }

  /**
   * Specifies the OID for the request associated with this extended operation.
   * This should only be called by pre-parse plugins.
   *
   * @param  requestOID  The OID for the request associated with this extended
   *                     operation.
   */
  @Override
  public final void setRequestOID(String requestOID)
  {
    this.requestOID = requestOID;
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
  public final ByteString getRequestValue()
  {
    return requestValue;
  }

  /**
   * Specifies the value for the request associated with this extended
   * operation.  This should only be called by pre-parse plugins.
   *
   * @param  requestValue  The value for the request associated with this
   *                       extended operation.
   */
  @Override
  public final void setRequestValue(ByteString requestValue)
  {
    this.requestValue = requestValue;
  }

  @Override
  public final String getResponseOID()
  {
    return responseOID;
  }

  @Override
  public final void setResponseOID(String responseOID)
  {
    this.responseOID = responseOID;
  }

  @Override
  public final ByteString getResponseValue()
  {
    return responseValue;
  }

  @Override
  public final void setResponseValue(ByteString responseValue)
  {
    this.responseValue = responseValue;
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.
    return OperationType.EXTENDED;
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

  /**
   * Performs the work of actually processing this operation.  This
   * should include all processing for the operation, including
   * invoking plugins, logging messages, performing access control,
   * managing synchronization, and any other work that might need to
   * be done in the course of processing.
   */
  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    logExtendedRequest(this);

    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse extended plugins.
      if (!processOperationResult(getPluginConfigManager().invokePreParseExtendedPlugins(this)))
      {
        return;
      }

      checkIfCanceled(false);

      // Get the extended operation handler for the request OID.  If there is
      // none, then fail.
      ExtendedOperationHandler<?> handler =
           DirectoryServer.getExtendedOperationHandler(requestOID);
      if (handler == null)
      {
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_EXTENDED_NO_HANDLER.get(requestOID));
        return;
      }

      // Look at the controls included in the request and ensure that all
      // critical controls are supported by the handler.
      for (Iterator<Control> iter = getRequestControls().iterator(); iter.hasNext();)
      {
        final Control c = iter.next();
        try
        {
          if (!getAccessControlHandler().isAllowed(getAuthorizationDN(), this, c))
          {
            // As per RFC 4511 4.1.11.
            if (c.isCritical())
            {
              setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
              appendErrorMessage(ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(c.getOID()));
            }
            else
            {
              // We don't want to process this non-critical control, so remove it.
              iter.remove();
              continue;
            }
          }
        }
        catch (DirectoryException e)
        {
          setResultCode(e.getResultCode());
          appendErrorMessage(e.getMessageObject());
          return;
        }

        if (!c.isCritical())
        {
          // The control isn't critical, so we don't care if it's supported
          // or not.
        }
        else if (!handler.supportsControl(c.getOID()))
        {
          setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
          appendErrorMessage(ERR_EXTENDED_UNSUPPORTED_CRITICAL_CONTROL.get(requestOID, c.getOID()));
          return;
        }
      }

      // Check to see if the client has permission to perform the
      // extended operation.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.
      try
      {
        if (!getAccessControlHandler().isAllowed(this))
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_EXTENDED_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(requestOID));
          return;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        return;
      }

      try
      {
        // Invoke the pre-operation extended plugins.
        if (!processOperationResult(getPluginConfigManager().invokePreOperationExtendedPlugins(this)))
        {
          return;
        }

        checkIfCanceled(false);

        // Actually perform the processing for this operation.
        handler.processExtendedOperation(this);
      }
      finally
      {
        getPluginConfigManager().invokePostOperationExtendedPlugins(this);
      }
    }
    catch(CanceledOperationException coe)
    {
      logger.traceException(coe);

      setResultCode(ResultCode.CANCELLED);
      cancelResult = new CancelResult(ResultCode.CANCELLED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the extended response.
      logExtendedResponse(this);

      // Send the response to the client.
      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELLED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }

      if(requestOID.equals(OID_START_TLS_REQUEST))
      {
        clientConnection.finishStartTLS();
      }

      // Invoke the post-response extended plugins.
      getPluginConfigManager().invokePostResponseExtendedPlugins(this);

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
  }

  private AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("ExtendedOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", oid=");
    buffer.append(requestOID);
    buffer.append(")");
  }
}
