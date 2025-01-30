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
 * Copyright 2025 3A Systems, LLC
 */
package org.opends.server.extensions;

import com.forgerock.opendj.ldap.extensions.EndTransactionExtendedRequest;
import com.forgerock.opendj.ldap.extensions.EndTransactionExtendedResult;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.EndTransactionExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.operation.RollbackOperation;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;


public class EndTransactionExtendedOperation extends ExtendedOperationHandler<EndTransactionExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  public EndTransactionExtendedOperation()
  {
    super();
  }

  @Override
  public void initializeExtendedOperationHandler(EndTransactionExtendedOperationHandlerCfg config) throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

  @Override
  public void processExtendedOperation(ExtendedOperation operation)
  {
    final EndTransactionExtendedRequest request =new EndTransactionExtendedRequest();
    if (operation.getRequestValue()!= null) {
        final ASN1Reader reader = ASN1.getReader(operation.getRequestValue());
        try {
            reader.readStartSequence();
            if (reader.hasNextElement()&& (reader.peekType() == ASN1.UNIVERSAL_BOOLEAN_TYPE)) {
              request.setCommit(reader.readBoolean());
            }
            request.setTransactionID(reader.readOctetStringAsString());
            reader.readEndSequence();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
    }

    final ClientConnection.Transaction trn=operation.getClientConnection().getTransaction(request.getTransactionID());
    if (trn==null) {
        operation.setResultCode(ResultCode.CANCELLED);
        operation.appendErrorMessage(LocalizableMessage.raw("unknown transactionId="+request.getTransactionID()));
        return;
    }

    final EndTransactionExtendedResult res=EndTransactionExtendedResult.newResult(ResultCode.SUCCESS);
    operation.setResultCode(res.getResultCode());
    Operation currentOperation=null;
    try {
        while((currentOperation=trn.getWaiting().poll())!=null) {
            if (request.isCommit()) {
                currentOperation.run();
                if (!ResultCode.SUCCESS.equals(currentOperation.getResultCode())) {
                    throw new InterruptedException();
                }
                currentOperation.operationCompleted();
                //res.success(currentOperation.getMessageID(),currentOperation.getResponseControls());
            }
        }
    }catch (Throwable e){
        res.setFailedMessageID(currentOperation.getMessageID());
        operation.setResultCode(currentOperation.getResultCode());
        operation.setErrorMessage(currentOperation.getErrorMessage());
        //rollback
        RollbackOperation cancelOperation=null;
        while((cancelOperation=trn.getCompleted().pollLast())!=null) {
            try {
                cancelOperation.rollback();
            }catch (Throwable e2){
                throw new RuntimeException("rollback error",e2);
            }
        }
    }finally {
        trn.clear();
    }
    operation.setResponseOID(res.getOID());
    operation.setResponseValue(res.getValue());
  }

  @Override
  public String getExtendedOperationOID()
  {
    return EndTransactionExtendedRequest.END_TRANSACTION_REQUEST_OID;
  }

  @Override
  public String getExtendedOperationName()
  {
    return "End Transaction";
  }
}
