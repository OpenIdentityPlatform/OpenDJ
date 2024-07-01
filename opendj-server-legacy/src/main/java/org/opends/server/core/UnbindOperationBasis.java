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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;

import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationUnbindOperation;
import org.opends.server.types.operation.PreParseUnbindOperation;

/**
 * This class defines an operation that may be used to close the connection
 * between the client and the Directory Server.
 */
public class UnbindOperationBasis
       extends AbstractOperation
       implements UnbindOperation,
                  PreParseUnbindOperation,
                  PostOperationUnbindOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
  public UnbindOperationBasis(ClientConnection clientConnection,
                         long operationID,
                         int messageID, List<Control> requestControls)
  {
    super(clientConnection, operationID, messageID, requestControls);

    cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL,
        ERR_CANNOT_CANCEL_UNBIND.get());
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.
    return OperationType.UNBIND;
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
  public final List<Control> getResponseControls()
  {
    // An unbind operation can never have a response, so just return an empty
    // list.
    return NO_RESPONSE_CONTROLS;
  }

  @Override
  public final void addResponseControl(Control control)
  {
    // An unbind operation can never have a response, so just ignore this.
  }

  @Override
  public final void removeResponseControl(Control control)
  {
    // An unbind operation can never have a response, so just ignore this.
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
    setProcessingStartTime();

    // Invoke the pre-parse unbind plugins.  We don't care about the result
    // since we're going to close the connection anyway.
    getPluginConfigManager().invokePreParseUnbindPlugins(this);

    logUnbind(this);

    // Check the set of controls included in the request.  If there are any,
    // see if any special processing is needed.
    // NYI

    // Disconnect the client.
    getClientConnection().disconnect(DisconnectReason.UNBIND, false, null);

    // Invoke the post-operation unbind plugins.
    getPluginConfigManager().invokePostOperationUnbindPlugins(this);

    setProcessingStopTime();
  }

  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("UnbindOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(")");
  }
}
