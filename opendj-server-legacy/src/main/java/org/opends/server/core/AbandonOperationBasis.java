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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationAbandonOperation;
import org.opends.server.types.operation.PreParseAbandonOperation;

/**
 * This class defines an operation that may be used to abandon an operation
 * that may already be in progress in the Directory Server.
 */
public class AbandonOperationBasis extends AbstractOperation
    implements AbandonOperation,
               PreParseAbandonOperation,
               PostOperationAbandonOperation
{
  /** The message ID of the operation that should be abandoned. */
  private final int idToAbandon;

  /**
   * Creates a new abandon operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  idToAbandon       The message ID of the operation that should be
   *                           abandoned.
   */
  public AbandonOperationBasis(
      ClientConnection clientConnection,
      long operationID,
      int messageID,
      List<Control> requestControls,
      int idToAbandon)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.idToAbandon = idToAbandon;
    this.cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_ABANDON.get());
  }

  /**
   * Retrieves the message ID of the operation that should be abandoned.
   *
   * @return  The message ID of the operation that should be abandoned.
   */
  @Override
  public final int getIDToAbandon()
  {
    return idToAbandon;
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
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.ABANDON;
  }

  @Override
  public final List<Control> getResponseControls()
  {
    // An abandon operation can never have a response, so just return an empty
    // list.
    return NO_RESPONSE_CONTROLS;
  }

  @Override
  public final void addResponseControl(Control control)
  {
    // An abandon operation can never have a response, so just ignore this.
  }

  @Override
  public final void removeResponseControl(Control control)
  {
    // An abandon operation can never have a response, so just ignore this.
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

    logAbandonRequest(this);

    // Create a labeled block of code that we can break out of if a problem is detected.
abandonProcessing:
    {
      // Invoke the pre-parse abandon plugins.
      if (!processOperationResult(getPluginConfigManager().invokePreParseAbandonPlugins(this)))
      {
        break abandonProcessing;
      }

      // Actually perform the abandon operation.  Make sure to set the result
      // code to reflect whether the abandon was successful and an error message
      // if it was not.  Even though there is no response, the result should
      // still be logged.
      // Even though it is technically illegal to send a response for
      // operations that have been abandoned, it may be a good idea to do so
      // to ensure that the requestor isn't left hanging.  This will be a
      // configurable option in the server.
      boolean notifyRequestor = DirectoryServer.notifyAbandonedOperations();

      LocalizableMessage cancelReason = INFO_CANCELED_BY_ABANDON_REQUEST.get(messageID);

      CancelRequest _cancelRequest = new CancelRequest(notifyRequestor,
                                                       cancelReason);

      CancelResult result = clientConnection.cancelOperation(idToAbandon,
                                                             _cancelRequest);

      setResultCode(result.getResultCode());
      appendErrorMessage(result.getResponseMessage());

      if (!processOperationResult(getPluginConfigManager().invokePostOperationAbandonPlugins(this)))
      {
        break abandonProcessing;
      }
    }

    // Stop the processing timer.
    setProcessingStopTime();

    // Log the result of the abandon operation.
    logAbandonResult(this);
  }

  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("AbandonOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", idToAbandon=");
    buffer.append(idToAbandon);
    buffer.append(")");
  }
}
