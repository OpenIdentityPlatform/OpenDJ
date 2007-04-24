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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.PostOperationUnbindOperation;
import org.opends.server.types.operation.PreParseUnbindOperation;

import static org.opends.server.loggers.Access.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines an operation that may be used to close the connection
 * between the client and the Directory Server.
 */
public class UnbindOperation
       extends Operation
       implements PreParseUnbindOperation, PostOperationUnbindOperation
{



  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;



  /**
   * Creates a new unbind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   */
  public UnbindOperation(ClientConnection clientConnection, long operationID,
                         int messageID, ArrayList<Control> requestControls)
  {
    super(clientConnection, operationID, messageID, requestControls);

  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.UNBIND;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, String message,
                                     int messageID)
  {
    // Since unbind operations can't be cancelled, we don't need to do anything
    // but forward the request on to the client connection.
    clientConnection.disconnect(disconnectReason, sendNotification, message,
                                messageID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    // There are no special elements that should be logged for an unbind
    // request.
    return new String[0][];
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    // There is no unbind response, nor are there any special elements that
    // should be logged when an unbind occurs.
    return new String[0][];
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final List<Control> getResponseControls()
  {
    // An unbind operation can never have a response, so just return an empty
    // list.
    return NO_RESPONSE_CONTROLS;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void addResponseControl(Control control)
  {
    // An unbind operation can never have a response, so just ignore this.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void removeResponseControl(Control control)
  {
    // An unbind operation can never have a response, so just ignore this.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getProcessingStartTime()
  {
    return processingStartTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getProcessingStopTime()
  {
    return processingStopTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getProcessingTime()
  {
    return (processingStopTime - processingStartTime);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void run()
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;

    processingStartTime = System.currentTimeMillis();


    // Invoke the pre-parse unbind plugins.  We don't care about the result
    // since we're going to close the connection anyway.
    pluginConfigManager.invokePreParseUnbindPlugins(this);


    // Log the unbind request.
    logUnbind(this);


    // Check the set of controls included in the request.  If there are any,
    // see if any special processing is needed.
    // NYI


    // Disconnect the client.
    getClientConnection().disconnect(DisconnectReason.UNBIND, false, null, -1);


    // Invoke the post-operation unbind plugins.
    pluginConfigManager.invokePostOperationUnbindPlugins(this);

    processingStopTime = System.currentTimeMillis();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelResult cancel(CancelRequest cancelRequest)
  {
    cancelRequest.addResponseMessage(getMessage(MSGID_CANNOT_CANCEL_UNBIND));
    return CancelResult.CANNOT_CANCEL;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelRequest getCancelRequest()
  {
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  protected boolean setCancelRequest(CancelRequest cancelRequest)
  {
    // Unbind operations cannot be canceled.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {
    buffer.append("UnbindOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(")");
  }
}

