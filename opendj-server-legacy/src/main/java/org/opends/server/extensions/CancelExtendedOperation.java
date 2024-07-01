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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.server.CancelExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ExtendedOperation;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements the LDAP cancel extended operation defined in RFC 3909.
 * It is similar to the LDAP abandon operation, with the exception that it
 * requires a response for both the operation that is cancelled and the cancel
 * request (whereas an abandon request never has a response, and if it is
 * successful the abandoned operation won't get one either).
 */
public class CancelExtendedOperation
       extends ExtendedOperationHandler<CancelExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Create an instance of this cancel extended operation.  All initialization
   * should be performed in the <CODE>initializeExtendedOperationHandler</CODE>
   * method.
   */
  public CancelExtendedOperation()
  {
    super();
  }

  @Override
  public void initializeExtendedOperationHandler(
                   CancelExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  @Override
  public void processExtendedOperation(ExtendedOperation operation)
  {
    // The value of the request must be a sequence containing an integer element
    // that holds the message ID of the operation to cancel.  If there is no
    // value or it cannot be decoded, then fail.
    int idToCancel;
    ByteString requestValue = operation.getRequestValue();
    if (requestValue == null)
    {
      operation.setResultCode(ResultCode.PROTOCOL_ERROR);
      operation.appendErrorMessage(ERR_EXTOP_CANCEL_NO_REQUEST_VALUE.get());
      return;
    }

    try
    {
      ASN1Reader reader = ASN1.getReader(requestValue);
      reader.readStartSequence();
      idToCancel = (int)reader.readInteger();
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      operation.setResultCode(ResultCode.PROTOCOL_ERROR);

      LocalizableMessage message = ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE.get(
              getExceptionMessage(e));
      operation.appendErrorMessage(message);
      return;
    }

    // Create the cancel request for the target operation.
    LocalizableMessage cancelReason =
        INFO_EXTOP_CANCEL_REASON.get(operation.getMessageID());
    CancelRequest cancelRequest = new CancelRequest(true, cancelReason);

    // Get the client connection and attempt the cancel.
    ClientConnection clientConnection = operation.getClientConnection();
    CancelResult cancelResult = clientConnection.cancelOperation(idToCancel,
                                                                 cancelRequest);

    // Update the result of the extended operation and return.
    ResultCode resultCode = cancelResult.getResultCode();
    operation.setResultCode(resultCode == ResultCode.CANCELLED
                                ? ResultCode.SUCCESS : resultCode);
    operation.appendErrorMessage(cancelResult.getResponseMessage());
  }

  @Override
  public String getExtendedOperationOID()
  {
    return OID_CANCEL_REQUEST;
  }

  @Override
  public String getExtendedOperationName()
  {
    return "Cancel";
  }
}
